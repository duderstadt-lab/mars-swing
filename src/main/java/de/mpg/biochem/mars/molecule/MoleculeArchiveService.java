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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import io.scif.services.FormatService;

import org.scijava.app.StatusService;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.plugin.AbstractPTService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.service.Service;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.table.MARSResultsTable;
import net.imagej.ImageJService;
import net.imagej.display.WindowService;

@Plugin(type = Service.class)
public class MoleculeArchiveService extends AbstractPTService<MoleculeArchiveService> implements ImageJService {
		
    @Parameter
    private UIService uiService;
    
    @Parameter
    private LogService logService;
    
    @Parameter
	private FormatService formatService;
    
    @Parameter
    private StatusService statusService;
    
    @Parameter
    private ScriptService scriptService;
    
    @Parameter
    private DisplayService displayService;
    
	private Map<String, MoleculeArchive> archives;
	
	@Override
	public void initialize() {
		// This Service method is called when the service is first created.
		archives = new LinkedHashMap<>();
		
		scriptService.addAlias(MoleculeArchive.class);
		scriptService.addAlias(MoleculeArchiveService.class);
	}
	
	public void addArchive(MoleculeArchive archive) {
		String name = archive.getName();
		int num = 1;	    
	    while (archives.containsKey(name)) {
	    	if (name.endsWith(".yama"))
	    		name = name.substring(0, name.length() - 5);
	    	if (num == 1) {
	    		name = name + num + ".yama";
	    	} else  {
	    		name = name.substring(0, name.length() - String.valueOf(num-1).length()) + num + ".yama";
	    	}
	    	num++;
	    }
	    
	    archive.setName(name);
		archives.put(archive.getName(), archive);
	}
	
	public void removeArchive(String title) {
		if (archives.containsKey(title)) {
			archives.get(title).destroy();
			archives.remove(title);		
			displayService.getDisplay(title).close();
		}
	}
	
	public void removeArchive(MoleculeArchive archive) {
		if (archives.containsKey(archive.getName())) {
			removeArchive(archive.getName());
			displayService.getDisplay(archive.getName()).close();
		}
	}
	
	public boolean rename(String oldName, String newName) {
		if (archives.containsKey(newName)) {
			logService.error("A MoleculeArchive is already open with that name. Choose another name.");
			return false;
		} else {
			archives.get(oldName).setName(newName);
			MoleculeArchive arch = archives.remove(oldName);
			archives.put(newName, arch);
			displayService.getDisplay(oldName).setName(newName);
			return true;
		}
	}
	
	public void show(String name, MoleculeArchive archive) {
		//This will make sure we don't try to open archive windows if we are running in headless mode...
		//If this method is always used for showing archives it will seamlessly allow the same code to 
		//work in headless mode...
		if (!archives.containsKey(name))
			addArchive(archive);
		if (!uiService.isHeadless()) {
			if (archives.get(name).getWindow() != null) {
				archives.get(name).getWindow().updateAll();
			} else {
				MoleculeArchiveWindow win = new MoleculeArchiveWindow(archive, this);
				archives.get(name).setWindow(win);
			}
		}
	}
	
	public ArrayList<String> getColumnNames() {
		ArrayList<String> columns = new ArrayList<String>();
	
		for (MoleculeArchive archive: archives.values()) {
			//We assume all the molecules have the same columns
			//I think this should be strictly enforced
			MARSResultsTable datatable = archive.get(0).getDataTable();
			
			for (int i=0;i<datatable.getColumnCount();i++) {
				if(!columns.contains(datatable.getColumnHeader(i)))
					columns.add(datatable.getColumnHeader(i));
			}
		}
		
		return columns;
	}
	
	public ArrayList<String> getSegmentTableNames() {
		ArrayList<String> segTableNames = new ArrayList<String>();
	
		for (MoleculeArchive archive: archives.values()) {
			//We assume all the molecules have the same segment tables
			//I think this should be strictly enforced
			for (String segTableName : archive.get(0).getSegmentTableNames()) {
				if(!segTableNames.contains(segTableName))
					segTableNames.add(segTableName);
			}
		}
		
		return segTableNames;
	}
	
	
	public ArrayList<String> getArchiveNames() {
		return new ArrayList<String>(archives.keySet());
	}
	
	public boolean contains(String key) {
		return archives.containsKey(key);
	}
	
	public MoleculeArchive getArchive(String name) {
		return archives.get(name);
	}
	
	public MoleculeArchiveWindow getArchiveWindow(String name) {
		return archives.get(name).getWindow();
	}
	
	public UIService getUIService() {
		return uiService; 
	}
	
	public LogService getLogService() {
		return logService;
	}
	
	public StatusService getStatusService() {
		return statusService;
	}
	
	public FormatService getFormatService() {
		return formatService;
	}
	
	@Override
	public Class<MoleculeArchiveService> getPluginType() {
		return MoleculeArchiveService.class;
	}
}