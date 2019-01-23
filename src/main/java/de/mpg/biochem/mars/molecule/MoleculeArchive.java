/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import javax.swing.JOptionPane;

import org.apache.commons.io.FilenameUtils;

import org.scijava.log.LogService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.UIService;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.dataformat.smile.*;
import com.fasterxml.jackson.databind.*;

import de.mpg.biochem.mars.ImageProcessing.Peak;
import de.mpg.biochem.mars.table.GroupIndices;
import de.mpg.biochem.mars.table.ResultsTableService;
import de.mpg.biochem.mars.table.MARSResultsTable;
import de.mpg.biochem.mars.util.*;
import de.mpg.biochem.mars.*;
import io.scif.services.FormatService;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;
import net.openhft.chronicle.hash.serialization.impl.EnumMarshallable;
import net.openhft.chronicle.map.*;

import org.scijava.ui.*;
import static java.util.stream.Collectors.toList;

import org.scijava.table.*;

public class MoleculeArchive {
	private String name;
	
	//Services that the archive will need access to but that are not initialized..
	private MoleculeArchiveWindow win;
	private MoleculeArchiveService moleculeArchiveService;
	private ResultsTableService resultsTableService;
	private UIService uiService;
	private LogService logService;
	private File file;
	
	private MoleculeArchiveProperties archiveProperties;
	
	//This will maintain a list of the metaDatasets as an index with UID keys for each..
	//Need to make sure all write operations are placed within synchronized blocks. synchronized(imageMetaDataIndex) { ... }
	//To avoid thread issues.
	//All read operations can be done in parallel no problem.
	private ArrayList<String> imageMetaDataIndex;
	
	//This will store all the ImageMetaData sets associated with the molecules
	//molecules have a metadataUID that maps to these keys so it is clear which dataset they were from.
	private ConcurrentMap<String, ImageMetaData> imageMetaData;
	
	//Two different storage options for molecules
	//The chronicleMap is used if we are in virtual mode..
	private ChronicleMap<CharSequence, Molecule> archive;
	
	//This is a list of molecule keys that will define the index and be used for retrieval from the ChronicleMap in virtual memory
	//or retrieval from the molecules array in memory
	//This array defines the absolute set of molecules considered to be in the archive for purposes of saving and reading etc...
	//Need to make sure all write operations are placed within synchronized blocks. synchronized(moleculeIndex) { ... }
	//To avoid thread issues.
	//All read operations can be done in parallel no problem.
	private ArrayList<String> moleculeIndex;
	
	//This is a map index of tags for searching in molecule tables etc..
	private ConcurrentMap<String, String> tagIndex;
	
	//This is a map from keys to molecules if working in memory..
	//Otherwise if working in virtual memory it is left null..
	private ConcurrentMap<String, Molecule> molecules;
	
	private File persistedFile;
	
	//By default we work virtual
	private boolean virtual = true;
	
	//determines whether the file is encoded in binary smile format
	private boolean smileEncoding = true;
	
	//Constructor for creating an empty molecule archive...	
	public MoleculeArchive(String name) {
		this.name = name;
		this.virtual = false;
		
		initializeVariables();
		
		//We will load the archive into normal memory for faster processing...
		molecules = new ConcurrentHashMap<>();
	}
	
	//Constructor for creating an empty virtual molecule archive...
	public MoleculeArchive(String name, int numMolecules, double averageSize) {
		this.name = name;
		this.virtual = true;
		
		initializeVariables();
		
		//We will load the archive into virtual memory to allow for very large archive sizes...
		try {
			buildChronicleMap(numMolecules, averageSize);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//Constructor for loading a moleculeArchive from file...
	public MoleculeArchive(String name, File file, MoleculeArchiveService moleculeArchiveService, boolean virtual) throws JsonParseException, IOException {
		this.name = name;
		this.file = file;
		this.virtual = virtual;
		this.moleculeArchiveService = moleculeArchiveService;
		this.uiService = moleculeArchiveService.getUIService();
		this.logService = moleculeArchiveService.getLogService();
		
		initializeVariables();
		
		load(file);
	}
	
	//Constructor for building a molecule archive from table...
	public MoleculeArchive(String name, MARSResultsTable table, ResultsTableService resultsTableService, MoleculeArchiveService moleculeArchiveService, boolean virtual) {
		this.name = name;
		this.virtual = virtual;
		this.moleculeArchiveService = moleculeArchiveService;
		this.resultsTableService = resultsTableService;
		this.uiService = moleculeArchiveService.getUIService();
		this.logService = moleculeArchiveService.getLogService();
		
		initializeVariables();
		
		buildFromTable(table);
	}
	
	private void initializeVariables() {
		moleculeIndex = new ArrayList<String>();  
		imageMetaDataIndex = new ArrayList<String>(); 
		
		tagIndex = new ConcurrentHashMap<>();
		imageMetaData = new ConcurrentHashMap<>();
		
		archiveProperties = new MoleculeArchiveProperties(this);
	}
	
	private void load(File file) throws JsonParseException, IOException {
		//The first object in the yama file has general information about the archive including
		//number of Molecules and their averageSize, which we can use to initialize the ChronicleMap
		//if we are working virtual. So we load that information first
		InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
		
		//Here we automatically detect the format of the JSON file
		//Can be JSON text or Smile encoded binary file...
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory(); 
		DataFormatDetector det = new DataFormatDetector(new JsonFactory[] { jsonF, smileF });
	    DataFormatMatcher match = det.findFormat(inputStream);
	    JsonParser jParser = match.createParserWithMatch();
	    
	    if (match.getMatchedFormatName().equals("Smile")) {
	    	smileEncoding = true;
	    } else if (match.getMatchedFormatName().equals("JSON")) {
	    	//This is included just for completeness in case we want to
	    	//add a third format someday...
	    	smileEncoding = false;
	    } else {
	    	//We default to JSON
	    	smileEncoding = false;
	    }
		
		jParser.nextToken();
		jParser.nextToken();
		if ("MoleculeArchiveProperties".equals(jParser.getCurrentName())) {
			archiveProperties = new MoleculeArchiveProperties(jParser, this);
		} else {
			uiService.showDialog("No MoleculeArchiveProperties found. Are you sure this is a yama file?", MessageType.ERROR_MESSAGE);
			return;
		}
		
		// TODO need to add better defaults or through an error if no properties are found...
		int numMolecules = archiveProperties.getNumberOfMolecules();
		double averageSize = archiveProperties.getAverageMoleculeSize();
		
		boolean recover = false;
		if (virtual) {
			//We will load the archive into virtual memory to allow for very large archive sizes...
			recover = buildChronicleMap(numMolecules, averageSize);
		} else {
			//We will load the archive into normal memory for faster processing...
			molecules = new ConcurrentHashMap<>();
		}
		
		if (recover) {
			ArrayList<String> tempMoleculeIndex = new ArrayList<String>();
			for (CharSequence key : archive.keySet()) {
				if (key.toString().equals("MoleculeArchive Properties")) {
					//This is the special entry MoleculeArchive Properties...
					this.archiveProperties = archive.get(key).UnwrapMoleculeArchiveProperties();
				} else if (key.length() < 15) {
					//Must be an ImageMetaData item
					imageMetaDataIndex.add(key.toString());
				} else {
					tempMoleculeIndex.add(key.toString());
				}
			}
			//Here we sort to natural order
			moleculeIndex = (ArrayList<String>)tempMoleculeIndex.stream().sorted().collect(toList());
			archiveProperties.setNumberOfMolecules(moleculeIndex.size());
			generateTagIndex();
		} else {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jParser.getCurrentName();
				if ("ImageMetaData".equals(fieldName)) {
					while (jParser.nextToken() != JsonToken.END_ARRAY) {
						ImageMetaData imgMeta = new ImageMetaData(jParser);
						addImageMetaData(imgMeta);
					}
				}
				
				if ("Molecules".equals(fieldName)) {
					int molNum = 0;
					while (jParser.nextToken() != JsonToken.END_ARRAY) {
						add(new Molecule(jParser));
						molNum++;
						moleculeArchiveService.getStatusService().showStatus(molNum, numMolecules, "Loading molecules from " + file.getName());
					}
				}
			}
		}
		
		jParser.close();
		inputStream.close();
		
		//Once we are done reading we should update molecule archive properties
		updateArchiveProperties();		
	}
	
	private void buildFromTable(MARSResultsTable results) {
		//First we have to index the groups in the table to determine the number of Molecules and their average size...
		//Here we assume their is a molecule column that defines which data is related to which molecule.
		LinkedHashMap<Integer, GroupIndices> groups = ResultsTableService.find_group_indices(results, "molecule");
		int numMolecules = groups.size();
		
		//For averageSize let's just find the number of doubles in the table in total bytes...
		double averageSize = results.getRowCount()/numMolecules; //average number of rows per molecule
		averageSize *= (results.getColumnCount() - 1); //number of columns excluding the molecule column
		averageSize *= 8; //8 bytes per double
		
		//Should we add some extra space for other parameters?
		
		if (virtual) {
			//We will load the archive into virtual memory to allow for very large archive sizes...
			try {
				buildChronicleMap(numMolecules, averageSize, new File(System.getProperty("java.io.tmpdir") + "/" + name + ".store"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			//We will load the archive into normal memory for faster processing...
			molecules = new ConcurrentHashMap<>();
		}
		
		//We need to generate and add an ImageMetaData entry for the molecules from the the table
		//This will basically be empty, but as further processing steps occurs the log will be filled in
		//Also, the DataTable can be updated manually.
		String metaUID = MARSMath.getUUID58().substring(0, 10);
		ImageMetaData meta = new ImageMetaData(metaUID);
		addImageMetaData(meta);

		String[] headers = new String[results.getColumnCount() - 1];
		int col = 0;
		for (int i=0;i<results.getColumnCount();i++) {
			if (!results.getColumnHeader(i).equals("molecule")) {
				headers[col] = results.getColumnHeader(i);
				col++;
			}
		}
		
		//Now we need to build the archive from the table, molecule by molecule
		for (int mol: groups.keySet()) {
			MARSResultsTable molTable = new MARSResultsTable();
			for (String header: headers) {
				molTable.add(new DoubleColumn(header));
			}
			int row = 0;
			for (int j=groups.get(mol).start;j<=groups.get(mol).end;j++) {
				molTable.appendRow();
				col = 0;
				for (int i=0;i<results.getColumnCount();i++) {
					if (!results.getColumnHeader(i).equals("molecule")) {
						molTable.set(col, row, results.get(i, j));
						col++;
					}
				}
				row++;
			}
			Molecule molecule = new Molecule(MARSMath.getUUID58(), molTable);
			molecule.setImageMetaDataUID(metaUID);
			add(molecule);
		}
	}
	
	public void save() {
		saveAs(file);
	}
	
	public void saveAs(File file) {
		try {
			String filePath = file.getAbsolutePath();
			if (!filePath.endsWith(".yama")) {
				file = new File(filePath + ".yama");
			}
			
			OutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
			
			JsonGenerator jGenerator;
			if (smileEncoding) {
				SmileFactory jfactory = new SmileFactory();
				jGenerator = jfactory.createGenerator(stream);
			} else {
				JsonFactory jfactory = new JsonFactory();
				jGenerator = jfactory.createGenerator(stream, JsonEncoding.UTF8);
			}
			
			//We have to have a starting { for the json...
			jGenerator.writeStartObject();
			
			updateArchiveProperties();
			
			archiveProperties.toJSON(jGenerator);
			
			if (imageMetaDataIndex.size() > 0) {
				jGenerator.writeArrayFieldStart("ImageMetaData");
				Iterator<String> iter = imageMetaDataIndex.iterator();
				while (iter.hasNext()) {
					getImageMetaData(iter.next()).toJSON(jGenerator);
				}
				jGenerator.writeEndArray();
			}
			
			jGenerator.writeArrayFieldStart("Molecules");
			
			//loop through all molecules in ChronicleMap and save the data...
			Iterator<String> iterator = moleculeIndex.iterator();
			while (iterator.hasNext()) {
				get(iterator.next()).toJSON(jGenerator);
			}
			
			jGenerator.writeEndArray();
			
			//Now we need to add the corresponding global closing bracket } for the json format...
			jGenerator.writeEndObject();
			jGenerator.close();
			
			//flush and close streams...
			stream.flush();
			stream.close();
			
			//Reset the file and name
			this.file = file;
			setName(file.getName());
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private boolean buildChronicleMap(int numMolecules, double averageSize) throws IOException {
		persistedFile = new File(file.getParentFile() + "/" + name + ".store");
		return buildChronicleMap(numMolecules, averageSize, persistedFile);
	}
	
	//Returns true if using recover mode.
	private boolean buildChronicleMap(int numMolecules, double averageSize, File persistedFile) throws IOException {		
		this.persistedFile = persistedFile;
		
		boolean exists = persistedFile.exists();
		int recover = JOptionPane.NO_OPTION;
		//if (exists && !uiService.isHeadless()) {
		if (exists) {
			recover = JOptionPane.showConfirmDialog(null,
					"Recover from virtual store?", "Recovery Mode", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null);
			
			//recover = uiService.showDialog("Recover from virtual store?", "Recovery Mode", 
			//		DialogPrompt.MessageType.QUESTION_MESSAGE, DialogPrompt.OptionType.YES_NO_OPTION);
		}
		
		if (exists && recover == JOptionPane.YES_OPTION) {
			archive = ChronicleMap
				    .of(CharSequence.class, Molecule.class)
				    .valueMarshaller(MoleculeMarshaller.INSTANCE)
				    .name(name)
				    .averageKey("qwHsTzRnmY5oKwPNvnezZA")
				    .entries(numMolecules + 10)
				    .maxBloatFactor(2.0)
				    .averageValueSize(averageSize)
				    .recoverPersistedTo(persistedFile, true);
			return true;
		} else {
			if (exists)
				persistedFile.delete();
			archive = ChronicleMap
				    .of(CharSequence.class, Molecule.class)
				    .valueMarshaller(MoleculeMarshaller.INSTANCE)
				    .name(name)
				    .averageKey("qwHsTzRnmY5oKwPNvnezZA")
				    .entries(numMolecules + 10)
				    .maxBloatFactor(2.0)
				    .averageValueSize(averageSize)
				    .createPersistedTo(persistedFile);
			return false;
		}
		/*
		if (exists && recover == JOptionPane.YES_OPTION) {
			archive = ChronicleMap
				    .of(CharSequence.class, Molecule.class)
				    .valueMarshaller(MoleculeMarshaller.INSTANCE)
				    .name(name)
				    .averageKey("qwHsTzRnmY5oKwPNvnezZA")
				    .entries(numMolecules + 10)
				    .maxBloatFactor(2.0)
				    .averageValueSize(averageSize)
				    .recoverPersistedTo(persistedFile, true);
			return true;
		} else {
			if (exists)
				persistedFile.delete();
			archive = ChronicleMap
				    .of(CharSequence.class, Molecule.class)
				    .valueMarshaller(MoleculeMarshaller.INSTANCE)
				    .name(name)
				    .averageKey("qwHsTzRnmY5oKwPNvnezZA")
				    .entries(numMolecules + 10)
				    .maxBloatFactor(2.0)
				    .averageValueSize(averageSize)
				    .createPersistedTo(persistedFile);
			return false;
		}
		*/
	}
	
	//Method for adding molecules to the archive
	//A key assumption here is that we never try to add two molecules that have the same key
	//So the idea is that we would only ever call this method once for a molecule with a given UID.
	public void add(Molecule molecule) {
		//We should increment the numberOfMolecules and set the correct index for molecule
		if (moleculeIndex.contains(molecule.getUID())) {
			addLogMessage("The archive already contains the molecule " + molecule.getUID() + ".");
			//logService.info("The archive already contains the molecule " + molecule.getUID() + ".");
		} else {
			//Need to make sure all write operations to moleculeIndex 
			//are synchronized to avoid two threads working at the same time
			//during a write operation
			synchronized(moleculeIndex) {
				moleculeIndex.add(molecule.getUID());
			}
			archiveProperties.setNumberOfMolecules(moleculeIndex.size());
			if (virtual) {
				archive.put(molecule.getUID(), molecule);
			} else {
				molecules.put(molecule.getUID(), molecule);
				molecule.setParentArchive(this);
			}
			updateTagIndex(molecule);
		}
	}
	
	public void addImageMetaData(ImageMetaData metaData) {
		synchronized(imageMetaDataIndex) {
			imageMetaDataIndex.add(metaData.getUID());
		}
		if (virtual) {
			archive.put(metaData.getUID(), metaData.getMoleculeWrapper());
		} else {
			imageMetaData.put(metaData.getUID(), metaData);
		}
	}
	
	public void setImageMetaData(ImageMetaData metaData) {
		synchronized(imageMetaDataIndex) {
			if (!imageMetaDataIndex.contains(metaData.getUID()))
				imageMetaDataIndex.add(metaData.getUID());
		}
		if (virtual) {
			archive.put(metaData.getUID(), metaData.getMoleculeWrapper());
		} else {
			imageMetaData.put(metaData.getUID(), metaData);
		}
	}
	
	public void removeImageMetaData(String metaUID) {
		synchronized(imageMetaDataIndex) {
			if (!imageMetaDataIndex.contains(metaUID))
				imageMetaDataIndex.remove(metaUID);
		}
		if (virtual) {
			archive.remove(metaUID);
		} else {
			imageMetaData.remove(metaUID);
		}
	}

	public void removeImageMetaData(ImageMetaData meta) {
		removeImageMetaData(meta.getUID());
	}
	
	public ImageMetaData getImageMetaData(int index) {
		return getImageMetaData(imageMetaDataIndex.get(index));
	}
	
	public ImageMetaData getImageMetaData(String metaUID) {
		if (virtual) {
			return archive.get(metaUID).UnwrapImageMetaData();
		} else {
			return imageMetaData.get(metaUID);
		}
	}
	
	public ArrayList<String> getImageMetaDataUIDs() {
		return imageMetaDataIndex;
	}
	
	public int getNumberOfMolecules() {
		return moleculeIndex.size();
	}
	
	public double getAverageMoleculeSize() {
		return archiveProperties.getAverageMoleculeSize();
	}
	
	public int getNumberOfImageMetaDataItems() {
		return imageMetaDataIndex.size();
	}
	
	public String getStoreLocation() {
		return archive.file().getAbsolutePath();
	}
	
	public String getComments() {
		return archiveProperties.getComments();
	}
	
	public void setComments(String comments) {
		archiveProperties.setComments(comments);
		if (virtual) {
			archive.put("MoleculeArchive Properties", archiveProperties.getMoleculeWrapper());
		}
	}
	
	public boolean isVirtual() {
		return virtual;
	}

	//Retrieve molecule based on index
	public Molecule get(int index) {
		return get(moleculeIndex.get(index));
	}
	
	public ArrayList<String> getMoleculeUIDs() {
		return moleculeIndex;
	}
	
	public String getTagList(String UID) {
		if (UID == null)
			return null;
		else
			return tagIndex.get(UID);
	}
	
	public void set(Molecule molecule) {
		if (virtual) {
			archive.put(molecule.getUID(), molecule);	
		} else {
			//We do nothing because we always work with the actual archive copy if working in memory.
			//We would do the following, but doesn't make sense.
			//molecules.put(molecule.getUID(), molecule);
		}
		
		//In either case we should update the tag index for fast searching...
		updateTagIndex(molecule);
	}
	
	public void generateTagIndex() {
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		try {
	        forkJoinPool.submit(() -> moleculeIndex.parallelStream().forEach(UID -> { 
	        	if (virtual)
	        		updateTagIndex(archive.get(UID));
	        	else 
	        		updateTagIndex(molecules.get(UID));
	        })).get();
	   } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    	e.printStackTrace();
	   } finally {
	      forkJoinPool.shutdown();
	   }
	}
	
	private void updateTagIndex(Molecule molecule) {
		if (molecule.getTags() == null) {
			//Shouldn't happen..do nothing...
		} else if (molecule.getTags().size() > 0) {
			String tagList = "";
			for (String tag:molecule.getTags())
				tagList += tag + ", ";
			tagList = tagList.substring(0, tagList.length() - 2);
			tagIndex.put(molecule.getUID(), tagList);
		} else {
			tagIndex.remove(molecule.getUID());
		}
	}
	
	public void updateTagIndex(String updateUID) {
		Molecule molecule = get(updateUID);
		if (molecule.getTags() == null) {
			//Shouldn't happen..do nothing...
		} else if (molecule.getTags().size() > 0) {
			String tagList = "";
			for (String tag:molecule.getTags())
				tagList += tag + ", ";
			tagList = tagList.substring(0, tagList.length() - 2);
			tagIndex.put(molecule.getUID(), tagList);
		} else {
			tagIndex.remove(molecule.getUID());
		}
	}
	
	public void remove(String UID) {
		synchronized(moleculeIndex) {
			moleculeIndex.remove(UID);
		}
		if (virtual) {
			archive.remove(UID);
		} else {
			molecules.remove(UID);
		}
	}
	
	public void remove(Molecule molecule) {
		remove(molecule.getUID());
	}
	
	public void deleteMoleculesWithTag(String tag) {
		//We should do this with streams but for the moment this is faster
		ArrayList<String> newMoleculeIndex = new ArrayList<String>();
		
		for (String UID : moleculeIndex) {
			Molecule mol;
			if (virtual) {
				mol = archive.get(UID);
			} else {
				mol = molecules.get(UID);
			}
			
			if (mol.hasTag(tag)) {
				if (virtual) {
					archive.remove(UID);
				} else {
					molecules.remove(UID);
				}
			} else {
				newMoleculeIndex.add(mol.getUID());
			}
		}
		
		moleculeIndex = newMoleculeIndex;
		archiveProperties.setNumberOfMolecules(moleculeIndex.size());	
	}
	
	public boolean contains(String UID) {
		if (virtual) {
			return archive.containsKey(UID);
		} else {
			return molecules.containsKey(UID);
		}
	}

	//Retrieve molecule based on UUID58 key
	public Molecule get(String UID) {
		if (virtual) {
			return archive.get(UID);
		} else {
			return molecules.get(UID);
		}
	}
	
	public int getIndex(String UID) {
		return moleculeIndex.indexOf(UID);
	}
	
	public String getUIDAtIndex(int index) {
		return moleculeIndex.get(index);
	}
	
	public void destroy() {
		if (virtual) {
			archive.close();
			//persistedFile.delete();
		}
	}
	
	public File getFile() {
		return file;
	}
	
	public void setFile(File file) {
		this.file = file;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public MoleculeArchiveWindow getWindow() {
		return win;
	}
	
	public void setWindow(MoleculeArchiveWindow win) {
		this.win = win;
	}
	
	public void lock() {
		if (win != null) {
			win.lockArchive();
		}
	}
	
	public void unlock() {
		if (win != null) {
			win.unlockArchive();
		}
	}
	
	public void setSMILEencoding() {
		smileEncoding = true;
	}
	
	public void unsetSMILEencoding() {
		smileEncoding = false;
	}
	
	public boolean isSMILEencoding() {
		return smileEncoding;
	}
	
	public void naturalOrderSortMoleculeIndex() {
		moleculeIndex = (ArrayList<String>)moleculeIndex.stream().sorted().collect(toList());
	}
	
	public MoleculeArchiveProperties getArchiveProperties() {
		return archiveProperties;
	}
	
	public void addLogMessage(String message) {
		for (String metaUID : imageMetaDataIndex) {
			if (virtual) {
				ImageMetaData meta = getImageMetaData(metaUID);
				meta.addLogMessage(message);
				setImageMetaData(meta);
			} else {
				imageMetaData.get(metaUID).addLogMessage(message);
			}
		}
	}
	
	//Utility functions
	public void updateArchiveProperties() {
		archiveProperties.setNumberOfMolecules(moleculeIndex.size());
		
		//Lets just sample a random 20 molecules and use that as the size...
		double averageSize = 0;
		if (moleculeIndex.size() == 0) {
			//leave it equal to zero...
		} else if (moleculeIndex.size() == 1) {
			averageSize = getByteSize(get(moleculeIndex.get(0)));
		} else {
			for (int i=0;i<20;i++) {
				int randomIndex = ThreadLocalRandom.current().nextInt(0, moleculeIndex.size() - 1);
				averageSize += getByteSize(get(moleculeIndex.get(randomIndex)));
			}
			
			averageSize = averageSize/20;
		}
		
		archiveProperties.setAverageMoleculeSize(averageSize);
		archiveProperties.setNumImageMetaData(imageMetaData.size());
		
		//At the moment this is only saved the store and never retrieved unless
		//Recovery mode is triggered..
		if (virtual) {
			archive.put("MoleculeArchive Properties", archiveProperties.getMoleculeWrapper());
		}
	}
	
	//average size of molecules in the archive based on 20 samples..
	//Computationally expensive
	//Since this is only important for the construction of the chronicle map.
	private double getByteSize(Molecule mol) {
		ByteArrayOutputStream sizeStream = new ByteArrayOutputStream();
		SmileGenerator jGenerator;
		//JsonGenerator jGenerator;
		
		double moleculeSize = -1;
		try {
			jGenerator = MoleculeMarshaller.jfactory.createGenerator(sizeStream);
			
			mol.toJSON(jGenerator);
			jGenerator.close();
			moleculeSize = sizeStream.toByteArray().length;
			sizeStream.close();		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return moleculeSize;
	}
	
	//Class for serialization of molecules into ChronticleMaps
	static final class MoleculeMarshaller implements BytesReader<Molecule>, BytesWriter<Molecule>,
    EnumMarshallable<MoleculeMarshaller> {
		public static final MoleculeMarshaller INSTANCE = new MoleculeMarshaller();
		//public static final JsonFactory jfactory = new JsonFactory();
		public static final SmileFactory jfactory = new SmileFactory();
		
		private MoleculeMarshaller() {}
		
		@Override
		public void write(Bytes out, Molecule toWrite) {
			try {
				OutputStream stream = out.outputStream();
				
				JsonGenerator jGenerator = jfactory.createGenerator(stream);
				toWrite.toJSON(jGenerator);
				
				jGenerator.close();
				
				stream.flush();
				stream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		@Override
		public Molecule read(Bytes in, Molecule using) {
			try {
				InputStream inputStream = in.inputStream();
		
				JsonParser jParser = jfactory.createParser(inputStream);
	
				using = new Molecule(jParser);
	
				jParser.close();
				inputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return using;
		}
		
		@Override
		public MoleculeMarshaller readResolve() {
		    return INSTANCE;
		}
	}
	
	@Override
	public String toString() {
		return name;
	}
}