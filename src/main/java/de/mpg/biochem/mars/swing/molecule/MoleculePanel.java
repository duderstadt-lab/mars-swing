/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.swing.molecule;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

//import org.decimal4j.util.DoubleRounder;
import org.scijava.log.LogService;

import de.mpg.biochem.mars.swing.plot.BoundsChangedListener;
import de.mpg.biochem.mars.swing.plot.CurvePlot;
import de.mpg.biochem.mars.swing.plot.PlotPanel;
import de.mpg.biochem.mars.swing.plot.PlotProperties;
import de.mpg.biochem.mars.table.MARSResultsTable;
import de.mpg.biochem.mars.molecule.*;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageLayout;

public class MoleculePanel extends JPanel implements BoundsChangedListener, MoleculeChangedListener {

	private JTextField UIDLabel, ImageMetaDataUIDLabel;
	
	private JTabbedPane dataANDPlot;
	private JScrollPane tablePane;
	
	private JTable DataTable;
	private AbstractTableModel DataTableModel;
	
	private HashMap<String, String> tagHotKeyList;
	
	//For single curve plotting
	private CurvePlot plotPanel;
	
	//For multicurve plotting.
	private ArrayList<PlotPanel> multiPlots = new ArrayList<PlotPanel>();
	private JPanel multiPlotPane;
	private boolean multiPlot = false;
	private int numberOfPlots = 2;
	
	//For videos.
	private JPanel videoPanel;
	
	private JTable ParameterTable;
	private AbstractTableModel ParameterTableModel;
	private String[] ParameterList;
	
	private JTable TagTable;
	private AbstractTableModel TagTableModel;
	private String[] TagList;
	
	private JTextArea notes;
	
	private JLabel recordCount;
	
	private Molecule molecule;
	private MoleculeArchive archive;
	
	private boolean moleculeRecordChanged;
	
	private JTable moleculeIndex;
	private AbstractTableModel moleculeIndexTableModel;
	private TableRowSorter moleculeSorter;

	private JTextField moleculeSearchField;
	
	private int moleculeCount;
	
	private Molecule DummyMolecule = new Molecule("unknown");
	
	public MoleculePanel(MoleculeArchive archive) {
		this.archive = archive;
		if (archive.getNumberOfMolecules() > 0) {
			molecule = archive.get(0);
		} else {
			molecule = DummyMolecule;
		}
		moleculeRecordChanged = false;
		
		DummyMolecule.setDataTable(new MARSResultsTable());
		DummyMolecule.setImageMetaDataUID("XXXXXXXXXX");
			
		moleculeCount = archive.getNumberOfMolecules();
		buildPanel();
		
		tagHotKeyList = new HashMap<String, String>();
	}
	
	private void buildPanel() {
		//MOLECULE INDEX LIST
		//Need to build the data model backed by the archive index...
		moleculeIndexTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public String getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0) {
					return "" + rowIndex;
				} else if (columnIndex == 1) {
					return archive.getUIDAtIndex(rowIndex);
				} else if (columnIndex == 2) {
					return archive.getTagList(archive.getUIDAtIndex(rowIndex));
				} else if (columnIndex == 3) {
					return archive.getImageMetaDataUIDforMolecule(archive.getUIDAtIndex(rowIndex));
				}
				return null;
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0) {
					return "Index";
				} else if (columnIndex == 1) {
					return "UID";
				} else if (columnIndex == 2) {
					return "Tags";
				} else if (columnIndex == 3) {
					return "metaUID";
				}
					return null;
			}

			@Override
			public int getRowCount() {
				return archive.getNumberOfMolecules();
			}
			
			@Override
			public int getColumnCount() {
				return 4;
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return false;
			}
		};
		
		moleculeIndex = new JTable(moleculeIndexTableModel);
		moleculeIndex.setFont(new Font("Menlo", Font.PLAIN, 12));
		moleculeIndex.setRowSelectionAllowed(true);
		moleculeIndex.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resizeColumnWidth(moleculeIndex);
		
		ListSelectionModel rowSM = moleculeIndex.getSelectionModel();
        rowSM.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
            	if (ParameterTable != null && ParameterTable.isEditing())
        			ParameterTable.getCellEditor().stopCellEditing();
                //Ignore extra messages.
                if (e.getValueIsAdjusting()) return;

                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                if (!lsm.isSelectionEmpty()) {
                    int selectedRow = lsm.getMinSelectionIndex();
                    if (moleculeRecordChanged)
                    	archive.put(molecule);
                    molecule = archive.get((String)moleculeIndex.getValueAt(selectedRow, 1));
                    updateAll();
                }
            }
        });

		moleculeIndex.getColumnModel().getColumn(0).setMinWidth(40);
		moleculeIndex.getColumnModel().getColumn(1).setMinWidth(150);
		
		JScrollPane moleculeIndexScrollPane = new JScrollPane(moleculeIndex);

		JPanel westPane = new JPanel();
		westPane.setLayout(new BorderLayout());
		
		moleculeSorter = new TableRowSorter<AbstractTableModel>(moleculeIndexTableModel);
		for (int i=0;i<moleculeIndexTableModel.getColumnCount();i++)
			moleculeSorter.setSortable(i, false);
		
		moleculeIndex.setRowSorter(moleculeSorter);
		
		moleculeSearchField = new JTextField();
		
		moleculeSearchField.getDocument().addDocumentListener(
	        new DocumentListener() {
	            public void changedUpdate(DocumentEvent e) {
	                filterMoleculeIndex();
	            }
	            public void insertUpdate(DocumentEvent e) {
	            	filterMoleculeIndex();
	            }
	            public void removeUpdate(DocumentEvent e) {
	            	filterMoleculeIndex();
	            }
	        });
		
		recordCount = new JLabel(moleculeSorter.getViewRowCount() + " molecules");
		recordCount.setBorder(new EmptyBorder(2,2,2,2));
		
		westPane.add(recordCount, BorderLayout.NORTH);
		westPane.add(moleculeIndexScrollPane, BorderLayout.CENTER);
		westPane.add(moleculeSearchField, BorderLayout.SOUTH);
		
		//PROPERTIES OF MOLECULE AT INDEX
		//This properties panel will be a JSplitPane on the right side.
		//That contains UID, Metadata, etc...
		JPanel propsPanel = buildPropertiesPanel();
		
		//Now we build the middle tabbed panel with all the tables
		dataANDPlot = new JTabbedPane();
		tablePane = buildDataTable();
		dataANDPlot.addTab("DataTable", tablePane);
		
		//Now we need to build the global layout with splipanes
		//First we build a right splitpane
		JPanel rightPane = new JPanel();
		rightPane.setLayout(new BorderLayout());
		rightPane.add(dataANDPlot, BorderLayout.CENTER);
		rightPane.add(propsPanel, BorderLayout.EAST);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				westPane, rightPane);
		
		splitPane.setDividerLocation(300);
		
		if (archive.getNumberOfMolecules() > 0)
			moleculeIndex.setRowSelectionInterval(0, 0);
		
		setLayout(new BorderLayout());
		add(splitPane, BorderLayout.CENTER);
		
		updateAll();
	}
	
	private JScrollPane buildDataTable() {
		DataTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0)
					return rowIndex + 1;
				
				return molecule.getDataTable().getValue(columnIndex - 1, rowIndex);
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0)
					return "Row";
				
				return molecule.getDataTable().getColumnHeader(columnIndex - 1);
			}

			@Override
			public int getRowCount() {
				return molecule.getDataTable().getRowCount();
			}
			
			@Override
			public int getColumnCount() {
				return molecule.getDataTable().getColumnCount() + 1;
			}
			
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				double value = Double.parseDouble((String)aValue);
				molecule.getDataTable().set(columnIndex - 1, rowIndex, value);
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return false;
			}
		};
		
		DataTable = new JTable(DataTableModel);
		DataTable.setAutoCreateColumnsFromModel(true);
		DataTable.setRowSelectionAllowed(true);
		DataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		
		resizeColumnWidth(DataTable);
		DataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		
		JScrollPane scrollPane = new JScrollPane(DataTable);
		
		return scrollPane;
	}
	
	public JPanel buildPropertiesPanel() {
		
		JPanel globalPan = new JPanel();
		globalPan.setLayout(new BorderLayout());
		
		JPanel northPan = new JPanel();
		northPan.setLayout(new GridBagLayout());
		GridBagConstraints gbcNorth = new GridBagConstraints();
		gbcNorth.anchor = GridBagConstraints.NORTH;
		
		gbcNorth.weightx = 1;
		gbcNorth.weighty = 1;
		
		gbcNorth.gridx = 0;
		gbcNorth.gridy = 0;
		
		gbcNorth.insets = new Insets(5, 0, 5, 0);
		
		//UID and image UID for this molecule
		JLabel uidName = new JLabel("UID");
		uidName.setFont(new Font("Menlo", Font.BOLD, 12));
		northPan.add(uidName, gbcNorth);
		
		gbcNorth.gridy += 1;
		UIDLabel = new JTextField("" + molecule.getUID());
		UIDLabel.setFont(new Font("Menlo", Font.PLAIN, 12));
		UIDLabel.setEditable(false);
		UIDLabel.setBackground(null);
		int UID_StringWidth = molecule.getUID().length() * 8;
		Dimension UID_dim = new Dimension(UID_StringWidth, 16);
		UIDLabel.setMinimumSize(UID_dim);
		northPan.add(UIDLabel, gbcNorth);
		
		gbcNorth.gridy += 1;
		JLabel imageMetaName = new JLabel("ImageMetaDataUID");
		imageMetaName.setFont(new Font("Menlo", Font.BOLD, 12));
		northPan.add(imageMetaName, gbcNorth);
		
		gbcNorth.gridy += 1;
		gbcNorth.insets = new Insets(5, 0, 10, 0);
		ImageMetaDataUIDLabel = new JTextField("" + molecule.getImageMetaDataUID());
		ImageMetaDataUIDLabel.setFont(new Font("Menlo", Font.PLAIN, 12));
		ImageMetaDataUIDLabel.setEditable(false);
		ImageMetaDataUIDLabel.setBackground(null);
		int MetaUID_StringWidth = molecule.getImageMetaDataUID().length() * 8;
		Dimension MetaUID_dim = new Dimension(MetaUID_StringWidth, 16);
		ImageMetaDataUIDLabel.setMinimumSize(MetaUID_dim);
		northPan.add(ImageMetaDataUIDLabel, gbcNorth);
		
		globalPan.add(northPan, BorderLayout.NORTH);
		
		//This will be placed in the center...
		JPanel paramPanel = buildParameterPanel();
		globalPan.add(paramPanel, BorderLayout.CENTER);
		
		JPanel southPan = new JPanel();
		southPan.setLayout(new GridBagLayout());
		GridBagConstraints gbcSouth = new GridBagConstraints();
		gbcSouth.anchor = GridBagConstraints.NORTH;
		
		gbcSouth.weightx = 1;
		gbcSouth.weighty = 1;
		
		gbcSouth.gridx = 0;
		gbcSouth.gridy = 0;
		
		JPanel tagsPanel = buildTagsPanel();
		southPan.add(tagsPanel, gbcSouth);
		
		gbcSouth.gridy += 1;
		gbcSouth.anchor = GridBagConstraints.CENTER;
		gbcSouth.insets = new Insets(5, 0, 5, 0);
		JLabel notesName = new JLabel("Notes");
		notesName.setFont(new Font("Menlo", Font.BOLD, 12));
		southPan.add(notesName, gbcSouth);
		
		notes = new JTextArea(molecule.getNotes());
        JScrollPane noteScroll = new JScrollPane(notes);
        
        notes.getDocument().addDocumentListener(
	        new DocumentListener() {
	            public void changedUpdate(DocumentEvent e) {
	                if (archive.getNumberOfMolecules() != 0) {
	                	moleculeRecordChanged = true;
	            		molecule.setNotes(notes.getText());
	                }
	            }
	            public void insertUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfMolecules() != 0) {
	            		moleculeRecordChanged = true;
	            		molecule.setNotes(notes.getText());
	            	}
	            }
	            public void removeUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfMolecules() != 0) {
	            		moleculeRecordChanged = true;
	            		molecule.setNotes(notes.getText());
	            	}
	            }
	        });
		
        Dimension dim = new Dimension(225, 70);
		
       // noteScroll.setMinimumSize(dim);
       // noteScroll.setMaximumSize(dim);
        noteScroll.setPreferredSize(dim);
        gbcSouth.gridy += 1;
        
        gbcSouth.insets = new Insets(0, 0, 15, 0);
        southPan.add(noteScroll, gbcSouth);
        
        globalPan.add(southPan, BorderLayout.SOUTH);
		
		return globalPan;
	}
	
	public JPanel buildParameterPanel() {
		updateParameterList();
		
		ParameterTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0) {
					return ParameterList[rowIndex];
				}
				
				return molecule.getParameters().get(ParameterList[rowIndex]);
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0)
					return "Parameter";
				else
					return "Value";
			}

			@Override
			public int getRowCount() {
				return ParameterList.length;
			}
			
			@Override
			public int getColumnCount() {
				return 2;
			}
			
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				moleculeRecordChanged = true;
				double value = Double.parseDouble((String)aValue);
				molecule.setParameter(ParameterList[rowIndex], value);
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return columnIndex > 0;
			}
		};
		
		JPanel parameterPanel = new JPanel();
		parameterPanel.setLayout(new BorderLayout());
		
		ParameterTable = new JTable(ParameterTableModel);
		ParameterTable.setAutoCreateColumnsFromModel(true);
		ParameterTable.setRowSelectionAllowed(true);
		ParameterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		resizeColumnWidth(ParameterTable);
		
		ParameterTable.getColumnModel().getColumn(0).setMinWidth(125);
		ParameterTable.getColumnModel().getColumn(1).setCellRenderer( new DecimalFormatRenderer() );
		
		JScrollPane ParameterScrollPane = new JScrollPane(ParameterTable);
		
		Dimension dim2 = new Dimension(225, 10000);
		
		//ParameterScrollPane.setMinimumSize(dim2);
		ParameterScrollPane.setMaximumSize(dim2);
		ParameterScrollPane.setPreferredSize(dim2);
		
		parameterPanel.add(ParameterScrollPane, BorderLayout.CENTER);
		
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new GridBagLayout());
		GridBagConstraints parameterPanelGBC = new GridBagConstraints();
		parameterPanelGBC.anchor = GridBagConstraints.NORTH;
		
		//Top, left, bottom, right
		parameterPanelGBC.insets = new Insets(5, 0, 5, 0);
		
		parameterPanelGBC.weightx = 1;
		parameterPanelGBC.weighty = 1;
		
		parameterPanelGBC.gridx = 0;
		parameterPanelGBC.gridy = 0;
		
		JPanel AddPanel = new JPanel();
		AddPanel.setLayout(new BorderLayout());
		JTextField newParameter = new JTextField(12);
		Dimension dimParm = new Dimension(200, 20);
		newParameter.setMinimumSize(dimParm);
		AddPanel.add(newParameter, BorderLayout.CENTER);
		JButton Add = new JButton("Add");
		Add.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!newParameter.getText().equals("") && archive.getNumberOfMolecules() != 0) {
					moleculeRecordChanged = true;
					molecule.setParameter(newParameter.getText().trim(), 0);
					updateParameterList();
					ParameterTableModel.fireTableDataChanged();
				}
			}
		});
		AddPanel.add(Add, BorderLayout.EAST);
		
		southPanel.add(AddPanel, parameterPanelGBC);
		
		JButton Remove = new JButton("Remove");
		Remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (ParameterTable.getSelectedRow() != -1 && archive.getNumberOfMolecules() != 0) {
					moleculeRecordChanged = true;
					String param = (String)ParameterTable.getValueAt(ParameterTable.getSelectedRow(), 0);
					molecule.removeParameter(param);
					updateParameterList();
					ParameterTableModel.fireTableDataChanged();
				}
			}
		});
		
		parameterPanelGBC.gridy += 1;
		parameterPanelGBC.anchor = GridBagConstraints.NORTHEAST;
		southPanel.add(Remove, parameterPanelGBC);
		
		parameterPanel.add(southPanel, BorderLayout.SOUTH);
		
		return parameterPanel;
	}
	
	public JPanel buildTagsPanel() {
		TagTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				return TagList[rowIndex];
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				return "Tag";
			}

			@Override
			public int getRowCount() {
				return TagList.length;
			}
			
			@Override
			public int getColumnCount() {
				return 1;
			}
		};
		
		JPanel tagPanel = new JPanel();
		tagPanel.setLayout(new GridBagLayout());
		
		GridBagConstraints tagPanelGBC = new GridBagConstraints();
		tagPanelGBC.anchor = GridBagConstraints.NORTH;
		//tagPanelGBC.insets = new Insets(5, 5, 5, 5);
		
		tagPanelGBC.weightx = 1;
		tagPanelGBC.weighty = 1;
		
		tagPanelGBC.gridx = 0;
		tagPanelGBC.gridy = 0;
		
		TagTable = new JTable(TagTableModel);
		TagTable.setAutoCreateColumnsFromModel(true);
		TagTable.setRowSelectionAllowed(true);
		TagTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		TagTable.getColumnModel().getColumn(0).sizeWidthToFit();
		
		//TagTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		
		JScrollPane TagScrollPane = new JScrollPane(TagTable);
		
		Dimension dim3 = new Dimension(225, 100);
		
		TagScrollPane.setMinimumSize(dim3);
		TagScrollPane.setMaximumSize(dim3);
		TagScrollPane.setPreferredSize(dim3);
		
		tagPanelGBC.gridx = 0;
		tagPanelGBC.gridy = 0;
		tagPanelGBC.insets = new Insets(0, 0, 0, 0);
		tagPanel.add(TagScrollPane, tagPanelGBC);
		
		JPanel AddPanel = new JPanel();
		AddPanel.setLayout(new BorderLayout());
		JTextField newTag = new JTextField(12);
		Dimension dimTag = new Dimension(200, 20);
		newTag.setMinimumSize(dimTag);
		AddPanel.add(newTag, BorderLayout.CENTER);
		JButton Add = new JButton("Add");
		Add.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!newTag.getText().equals("") && archive.getNumberOfMolecules() != 0) {
					moleculeRecordChanged = true;
					molecule.addTag(newTag.getText().trim());
					updateTagList();
					TagTableModel.fireTableDataChanged();
				}
			}
		});
		AddPanel.add(Add, BorderLayout.EAST);
		
		tagPanelGBC.gridx = 0;
		tagPanelGBC.gridy = 2;
		tagPanelGBC.insets = new Insets(5, 0, 5, 0);
		
		tagPanel.add(AddPanel, tagPanelGBC);
		
		JButton Remove = new JButton("Remove");
		Remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (TagTable.getSelectedRow() != -1 && archive.getNumberOfMolecules() != 0) {
					String tag = (String)TagTable.getValueAt(TagTable.getSelectedRow(), 0);
					moleculeRecordChanged = true;
					molecule.removeTag(tag);
					updateTagList();
					TagTableModel.fireTableDataChanged();
				}
			}
		});
		tagPanelGBC.gridx = 0;
		tagPanelGBC.gridy = 3;
		tagPanelGBC.anchor = GridBagConstraints.NORTHEAST;
		tagPanel.add(Remove, tagPanelGBC);
		
		return tagPanel;
	}
	
	public JScrollPane buildSegmentsTable(MARSResultsTable segmentsTable) {	
		AbstractTableModel SegmentTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0)
					return rowIndex + 1;
				
				return segmentsTable.getValue(columnIndex - 1, rowIndex);
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0)
					return "Row";
				
				return segmentsTable.getColumnHeader(columnIndex - 1);
			}

			@Override
			public int getRowCount() {
				return segmentsTable.getRowCount();
			}
			
			@Override
			public int getColumnCount() {
				return segmentsTable.getColumnCount() + 1;
			}
			
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				double value = Double.parseDouble((String)aValue);
				segmentsTable.set(columnIndex - 1, rowIndex, value);
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return false;
			}
		};
			
		JTable segTable = new JTable(SegmentTableModel);
		segTable.setAutoCreateColumnsFromModel(true);
		segTable.setRowSelectionAllowed(true);
		segTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		
		for (int i = 0; i < segTable.getColumnCount(); i++)
			segTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
		resizeColumnWidth(segTable);
		segTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		
		JScrollPane SegScrollPane = new JScrollPane(segTable);
		
		return SegScrollPane;
	}
	
	public void addCurvePlot(ArrayList<PlotProperties> props) {
		multiPlot = false;
		if (dataANDPlot.indexOfTab("Plot") != -1) 
			dataANDPlot.removeTabAt(dataANDPlot.indexOfTab("Plot"));
    	 
		plotPanel = new CurvePlot(props, molecule);
		plotPanel.getPlot().addMoleculeChangedListener(this);
    	plotPanel.showPlot();
	 	plotPanel.setName("Plot");
	 	dataANDPlot.add(plotPanel, 0);
	 	dataANDPlot.setSelectedIndex(0);
	}
	
	public void addMulitplePlots(int numberOfPlots, String xColumnName) {
		this.numberOfPlots = numberOfPlots;
		multiPlot = true;
		if (dataANDPlot.indexOfTab("Plot") != -1) 
			dataANDPlot.removeTabAt(dataANDPlot.indexOfTab("Plot"));
    	 
		Container contentPane = new Container();
		contentPane.setLayout(new GridLayout(numberOfPlots, 1));

		for (int i = 0; i < numberOfPlots; i++) {
			PlotPanel panel = new PlotPanel(xColumnName, molecule);
			panel.getPlot().addBoundsChangedListener(this);
			panel.getPlot().addMoleculeChangedListener(this);
			contentPane.add(panel);
			multiPlots.add(panel);
		}
		
		multiPlotPane = new JPanel();
		multiPlotPane.setLayout(new BorderLayout());
		multiPlotPane.add(contentPane, BorderLayout.CENTER);
		
		multiPlotPane.setName("Plot");
		
	 	dataANDPlot.add(multiPlotPane, 0);
	 	dataANDPlot.setSelectedIndex(0);
	}
	
	public void saveCurrentRecord() {
		archive.put(molecule);
	}

	public void updateAll() {
		if (archive.getNumberOfMolecules() == 0) {
			System.out.println("No molecules found");
			molecule = DummyMolecule;
			notes.setEditable(false);
		} else if (archive.get(molecule.getUID()) == null) {
			molecule = archive.get(0);
			notes.setEditable(true);
		} else {
			//Need to reload the current molecule if
			//working in virtual storage
			//This ensures if a command changed the values
			//The new values are loaded 
			//this prevents overwriting when switching records
			//in the window..
			molecule = archive.get(molecule.getUID());
			notes.setEditable(true);
		}
		moleculeRecordChanged = false;
		
		//Update index table in case tags were changed
		if (moleculeCount < archive.getNumberOfMolecules()) {
			moleculeIndexTableModel.fireTableRowsInserted(moleculeCount - 1, archive.getNumberOfMolecules() - 1);
			moleculeCount = archive.getNumberOfMolecules();
		} else if (moleculeCount > archive.getNumberOfMolecules()) {
			moleculeIndexTableModel.fireTableRowsDeleted(archive.getNumberOfMolecules() - 1, moleculeCount - 1);
			moleculeCount = archive.getNumberOfMolecules();
		}
			
		//Update all entries...
		if (moleculeCount != 0) {
			moleculeIndexTableModel.fireTableRowsUpdated(0, moleculeCount - 1);
		}
		
		recordCount.setText(moleculeSorter.getViewRowCount() + " molecules");
		recordCount.repaint();
		
		updateParameterList();
		updateTagList();
		
		//Update molecule labels
		UIDLabel.setText(molecule.getUID());
		UIDLabel.repaint();
		ImageMetaDataUIDLabel.setText(molecule.getImageMetaDataUID());
		ImageMetaDataUIDLabel.repaint();
		
		//Update DataTable
		DataTableModel.fireTableStructureChanged();
		resizeColumnWidth(DataTable);
		for (int i = 0; i < DataTable.getColumnCount(); i++)
			DataTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
		//Update Parameter list
		ParameterTableModel.fireTableDataChanged();
		ParameterTable.getColumnModel().getColumn(0).setMinWidth(125);
		ParameterTable.getColumnModel().getColumn(1).setCellRenderer( new DecimalFormatRenderer() );
		
		//Update TagList
		TagTableModel.fireTableDataChanged();
		TagTable.getColumnModel().getColumn(0).sizeWidthToFit();
		
		notes.setText(molecule.getNotes());
		
		int selectedTab = dataANDPlot.getSelectedIndex();
		dataANDPlot.removeAll();
		
		if (multiPlot) {
			for (PlotPanel plotPane : multiPlots) {
				plotPane.setMolecule(molecule);
				plotPane.showPlot();
			}
			dataANDPlot.addTab("Plot", multiPlotPane);
		} else if (plotPanel != null) {
			plotPanel.setMolecule(molecule);
			plotPanel.showPlot();
			dataANDPlot.addTab("Plot", plotPanel);
		}

		dataANDPlot.addTab("DataTable", tablePane);
		
		if (molecule.getSegmentTableNames().size() > 0) {
			for (ArrayList<String> tableColumnNames :molecule.getSegmentTableNames()) {
				JScrollPane segmentTablePane = buildSegmentsTable(molecule.getSegmentsTable(tableColumnNames));
				dataANDPlot.addTab(tableColumnNames.get(1) + " vs " + tableColumnNames.get(0), segmentTablePane);
			}
		}
		
		if (selectedTab < dataANDPlot.getTabCount())
			dataANDPlot.setSelectedIndex(selectedTab);
	}
	
	private void filterMoleculeIndex() {
		RowFilter<Object, Object> rf = null;
		List<RowFilter<Object,Object>> rfs = new ArrayList<RowFilter<Object,Object>>();

        try {
        	String searchString = moleculeSearchField.getText();
        	String[] searchlist = searchString.split(",");
        	
        	for (int i=0; i<searchlist.length; i++) {
                //rfs.add(RowFilter.regexFilter("(?=.*?(" + searchlist[i].trim() + "))"));
        		rfs.add(RowFilter.regexFilter(searchlist[i].trim()));
            }

            rf = RowFilter.andFilter(rfs);
        } catch (java.util.regex.PatternSyntaxException e) {
        	System.out.println("error " + e.getMessage());
            return;
        }
        moleculeSorter.setRowFilter(rf);
        recordCount.setText(moleculeSorter.getViewRowCount() + " molecules");
        recordCount.repaint();
        moleculeIndex.updateUI();
    }
	
	public void updateParameterList() {
		ParameterList = new String[molecule.getParameters().keySet().size()];
		molecule.getParameters().keySet().toArray(ParameterList);
	}
	
	public void updateTagList() {
		TagList = new String[molecule.getTags().size()];
		molecule.getTags().toArray(TagList);
	}
	
	public Molecule getMolecule() {
		return molecule;
	}
	
	public void resizeColumnWidth(JTable table) {
	    final TableColumnModel columnModel = table.getColumnModel();
	    for (int column = 0; column < table.getColumnCount(); column++) {
	        int width = 15; // Min width
	        for (int row = 0; row < table.getRowCount(); row++) {
	            TableCellRenderer renderer = table.getCellRenderer(row, column);
	            Component comp = table.prepareRenderer(renderer, row, column);
	            width = Math.max(comp.getPreferredSize().width +1 , width);
	        }
	        if(width > 300)
	            width=300;
	        columnModel.getColumn(column).setPreferredWidth(width);
	    }
	}
	

	public void updateTagHotKeyList(HashMap<String, String> newHotKeys) {
		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).clear();
		getActionMap().clear();
		
		this.tagHotKeyList = newHotKeys;
		for (String key : tagHotKeyList.keySet()) {
			addTagHotKey(key, tagHotKeyList.get(key));
		}
	}
	
	private void addTagHotKey(String keyStrokeAndKey, String tag) {
		KeyStroke keyStroke = KeyStroke.getKeyStroke(keyStrokeAndKey);
		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(keyStroke, keyStrokeAndKey);
		getActionMap().put(keyStrokeAndKey, new addTag(tag));
	}
	
	class addTag extends AbstractAction {
		private static final long serialVersionUID = 1L;
		String name;
		public addTag(String name) {
	        this.name = name;
	    }
	    public void actionPerformed(ActionEvent e) {
	        molecule.addTag(name);
	        moleculeRecordChanged = true;
	        updateTagList();
			TagTableModel.fireTableDataChanged();
	    }
	}
	
	@Override
	public void boundsChanged(Rectangle2D.Double bounds, int bleftMargin) {
		for (int i = 0; i < numberOfPlots; i++) {
			Rectangle2D.Double originalBounds = multiPlots.get(i).getPlot().getPlotBounds();
			originalBounds.x = bounds.x;
			originalBounds.width = bounds.width;
			multiPlots.get(i).getPlot().setPlotBounds(originalBounds);
			multiPlots.get(i).getPlot().leftMargin = bleftMargin;
		}
	}

	@Override
	public void MoleculeChanged(Molecule molecule) {
		updateParameterList();
		updateTagList();
		
		moleculeRecordChanged = true;
		
		//Update Parameter list
		ParameterTableModel.fireTableStructureChanged();
		ParameterTable.getColumnModel().getColumn(0).setMinWidth(125);
		ParameterTable.getColumnModel().getColumn(1).setCellRenderer( new DecimalFormatRenderer() );
		
		//Update TagList
		TagTableModel.fireTableStructureChanged();
		TagTable.getColumnModel().getColumn(0).sizeWidthToFit();
	}
	
	static class DecimalFormatRenderer extends DefaultTableCellRenderer {
		  private static final long serialVersionUID = 1L;
		  private static final DecimalFormat formatterSci = new DecimalFormat( "0.###E0" );
	      private static final DecimalFormat formatterNum = new DecimalFormat( "####.###" );
	      
	      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
	 
	    	  if ((double)value > 1000 || (double)value < -1000 || (double)value < 0.001 && !((double)value < -0.001)) {
	    		  value = formatterSci.format((Number)value);
	    	  } else {
	    		  value = formatterNum.format((Number)value);
	    	  }
	
	         return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column );
	      }
	}
}
