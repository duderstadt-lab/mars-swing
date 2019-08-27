/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.apache.commons.lang3.StringUtils;
import org.scijava.plugin.Parameter;
import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericColumn;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import de.mpg.biochem.mars.swing.molecule.MoleculePanel.DecimalFormatRenderer;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.molecule.*;

public class ImageMetadataPanel extends JPanel {
	private MarsImageMetadata imageMetadata;
	private MoleculeArchive<Molecule, MarsImageMetadata, MoleculeArchiveProperties> archive;
	
	private JTextField UIDLabel, DateLabel, SourcePath;
	private JTextField Microscope;
	private JTextArea Notes;
	
	private int imageMetadataCount;
	
	@Parameter
	private UIService uiService;
	
	//Log Tab Components
	private JScrollPane logTab;
	private JTextArea log;
	
	private JPanel bdvTab;
	
	private JTabbedPane metaDataTabs;
	
	private JTable imageMetadataIndex;
	private AbstractTableModel imageMetadataIndexTableModel;
	private TableRowSorter imageMetadataSorter;
	
	private JTextField imageMetadataSearchField;
	private JScrollPane imageMetadataProperties;
	
	private JTable DataTable;
	private AbstractTableModel DataTableModel;
	
	private JTable ParameterTable;
	private AbstractTableModel ParameterTableModel;
	private String[] ParameterList;
	
	private JTable BdvSourceTable;
	private AbstractTableModel BdvSourceTableModel;
	private String[] BdvSourceList;
	
	private JTable TagTable;
	private AbstractTableModel TagTableModel;
	private String[] TagList;
	
	private boolean imageMetadataRecordChanged = false;
	
	private SdmmImageMetadata DummyImageMetadata = new SdmmImageMetadata("unknown", new MarsTable());
	
	public ImageMetadataPanel(MoleculeArchive<Molecule, MarsImageMetadata, MoleculeArchiveProperties> archive, UIService uiService) {
		this.archive = archive;
		this.uiService = uiService;
		
		if (archive.getNumberOfImageMetadataRecords() > 0) {
			this.imageMetadata = archive.getImageMetadata(0);
		} else {
			imageMetadata = DummyImageMetadata;
		}

		imageMetadataCount = archive.getNumberOfImageMetadataRecords();
		buildPanel();
	}
	
	public void buildPanel() {
		//METADATA INDEX LIST
		//Need to build the index datamodel backed by the ImageMetadata...
		imageMetadataIndexTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public String getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0) {
					return "" + rowIndex;
				} else if (columnIndex == 1) {
					return archive.getImageMetadataUIDAtIndex(rowIndex);
				}  else if (columnIndex == 2) {
					return archive.getImageMetadataTagList(archive.getImageMetadataUIDAtIndex(rowIndex));
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
				}	
				return null;
			}

			@Override
			public int getRowCount() {
				return archive.getNumberOfImageMetadataRecords();
			}
			
			@Override
			public int getColumnCount() {
				return 3;
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return false;
			}
		};
		
		imageMetadataIndex = new JTable(imageMetadataIndexTableModel);
		imageMetadataIndex.setFont(new Font("Menlo", Font.PLAIN, 12));
		imageMetadataIndex.setRowSelectionAllowed(true);
		imageMetadataIndex.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resizeColumnWidth(imageMetadataIndex);
		
		ListSelectionModel rowIMD = imageMetadataIndex.getSelectionModel();
		rowIMD.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
            	if (ParameterTable != null && ParameterTable.isEditing())
        			ParameterTable.getCellEditor().stopCellEditing();
                //Ignore extra messages.
                if (e.getValueIsAdjusting()) return;

                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                if (!lsm.isSelectionEmpty()) {
                    int selectedRow = lsm.getMinSelectionIndex();
                    if (imageMetadataRecordChanged && archive.getNumberOfImageMetadataRecords() != 0)
                    	archive.putImageMetadata(imageMetadata);
                    imageMetadata = archive.getImageMetadata((String)imageMetadataIndex.getValueAt(selectedRow, 1));
                    updateAll();
                }
            }
        });

		//for (int i=0; i<imageMetadataIndex.getColumnCount();i++)
		//	imageMetadataIndex.getColumnModel().getColumn(i).sizeWidthToFit();
		
		imageMetadataIndex.getColumnModel().getColumn(0).setMinWidth(40);
		imageMetadataIndex.getColumnModel().getColumn(1).setMinWidth(70);
		
		JScrollPane imageMetadataIndexScrollPane = new JScrollPane(imageMetadataIndex);

		JPanel westPane = new JPanel();
		westPane.setLayout(new BorderLayout());
		
		imageMetadataSorter = new TableRowSorter<AbstractTableModel>(imageMetadataIndexTableModel);
		for (int i=0;i<imageMetadataIndexTableModel.getColumnCount();i++)
			imageMetadataSorter.setSortable(i, false);
		
		imageMetadataIndex.setRowSorter(imageMetadataSorter);
		
		imageMetadataSearchField = new JTextField();
		
		imageMetadataSearchField.getDocument().addDocumentListener(
	        new DocumentListener() {
	            public void changedUpdate(DocumentEvent e) {
	            	filterImageMetadataIndex();
	            }
	            public void insertUpdate(DocumentEvent e) {
	            	filterImageMetadataIndex();
	            }
	            public void removeUpdate(DocumentEvent e) {
	            	filterImageMetadataIndex();
	            }
	        });
		
		westPane.add(imageMetadataIndexScrollPane, BorderLayout.CENTER);
		westPane.add(imageMetadataSearchField, BorderLayout.SOUTH);
		
		metaDataTabs = new JTabbedPane();
		buildMetadataTabs();
		
		updateBdvSourceList();
		updateParameterList();
		updateTagList();
		
		//PROPERTIES OF IMAGE META DATA AT INDEX
		//This properties panel will be a JSplitPane on the right side.
		//That contains UID, Metadata, etc...
		JPanel propsPanel = buildPropertiesPanel();
		
		JPanel rightPane = new JPanel();
		rightPane.setLayout(new BorderLayout());
		rightPane.add(metaDataTabs, BorderLayout.CENTER);
		rightPane.add(propsPanel, BorderLayout.EAST);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, westPane, rightPane);
				
		splitPane.setDividerLocation(300);
		
		if (archive.getNumberOfImageMetadataRecords() > 0)
			imageMetadataIndex.setRowSelectionInterval(0, 0);
		
		setLayout(new BorderLayout());
		add(splitPane, BorderLayout.CENTER);
		
		updateAll();
	}
	
	public void buildMetadataTabs() {
		metaDataTabs.removeAll();
		
		imageMetadataProperties = buildMetadataProperties();
		metaDataTabs.addTab("Properties", imageMetadataProperties);
		
		JScrollPane tablePane = buildMetadataTable();
		metaDataTabs.addTab("DataTable", tablePane);
		
		//Notes
		Notes = new JTextArea(imageMetadata.getNotes());
        JScrollPane commentScroll = new JScrollPane(Notes);
		        
        Notes.getDocument().addDocumentListener(
	        new DocumentListener() {
	            public void changedUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfImageMetadataRecords() != 0) {
		            	imageMetadata.setNotes(Notes.getText());
		            	archive.putImageMetadata(imageMetadata);
	            	}
	            }
	            public void insertUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfImageMetadataRecords() != 0) {
		            	imageMetadata.setNotes(Notes.getText());
		            	archive.putImageMetadata(imageMetadata);
	            	}
	            }
	            public void removeUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfImageMetadataRecords() != 0) {
		            	imageMetadata.setNotes(Notes.getText());
		            	archive.putImageMetadata(imageMetadata);
	            	}
	            }
	        });
		metaDataTabs.addTab("Notes", commentScroll);
		
		logTab = makeLogTab();
		metaDataTabs.addTab("Log", logTab);	
		
		bdvTab = makeBdvTab();
		metaDataTabs.addTab("Bdv Views", bdvTab);	
	}
	
	public void updateBdvSourceList() {
		BdvSourceList = new String[imageMetadata.getBdvSources().size()];
		imageMetadata.getBdvSourceNames().toArray(BdvSourceList);
	}
	
	public JPanel makeBdvTab() {
		updateBdvSourceList();
		
		BdvSourceTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
	
			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				MarsBdvSource source = imageMetadata.getBdvSource(BdvSourceList[rowIndex]);
				switch(columnIndex) {
				  case 0:
					return BdvSourceList[rowIndex];
				  case 1:
					return source.getAffineTransform3D().get(0, 0);
				  case 2:
					return source.getAffineTransform3D().get(0, 1);
				  case 3:
					return source.getAffineTransform3D().get(0, 3);
				  case 4:
					return source.getAffineTransform3D().get(1, 0);
				  case 5:
					return source.getAffineTransform3D().get(1, 1);
				  case 6:
					return source.getAffineTransform3D().get(1, 3);
				  case 7:
					return source.getXDriftColumn();
				  case 8:
					return source.getYDriftColumn();
				  case 9:
					return source.getPathToXml();
				}

				return "";
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				switch(columnIndex) {
				  case 0:
					return "Name";
				  case 1:
					return "m01";
				  case 2:
					return "m02";
				  case 3:
					return "m03";
				  case 4:
					return "m10";
				  case 5:
					return "m11";
				  case 6:
					return "m12";
				  case 7:
					return "xDriftColumn";
				  case 8:
					return "yDriftColumn";
				  case 9:
					return "file path (xml)";
				}
				
				return "";
			}
			
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				MarsBdvSource source = imageMetadata.getBdvSource(BdvSourceList[rowIndex]);
				switch(columnIndex) {
				  case 0:
				    return;
				  case 1:
					source.getAffineTransform3D().set(Double.valueOf((String)aValue), 0, 0);
					return;
				  case 2:
					source.getAffineTransform3D().set(Double.valueOf((String)aValue), 0, 1);
					return;
				  case 3:
					source.getAffineTransform3D().set(Double.valueOf((String)aValue), 0, 3);
					return;
				  case 4:
					source.getAffineTransform3D().set(Double.valueOf((String)aValue), 1, 0);
					return;
				  case 5:
					source.getAffineTransform3D().set(Double.valueOf((String)aValue), 1, 1);
					return;
				  case 6:
					source.getAffineTransform3D().set(Double.valueOf((String)aValue), 1, 3);
					return;
				  case 7:
					source.setXDriftColumn((String)aValue);
					return;
				  case 8:
					source.setYDriftColumn((String)aValue);
					return;
				  case 9:
					source.setPathToXml((String)aValue);
					return;
				}
			}
	
			@Override
			public int getRowCount() {
				return BdvSourceList.length;
			}
			
			@Override
			public int getColumnCount() {
				return 10;
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				if (columnIndex == 0)
					return false;
				else
					return true;
			}
		};
		
		JPanel BdvSourcePanel = new JPanel();
		BdvSourcePanel.setLayout(new BorderLayout());
		
		BdvSourceTable = new JTable(BdvSourceTableModel);
		BdvSourceTable.setAutoCreateColumnsFromModel(true);
		BdvSourceTable.setRowSelectionAllowed(true);
		BdvSourceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resizeColumnWidth(BdvSourceTable);
		BdvSourceTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		
		for (int i = 0; i < BdvSourceTable.getColumnCount(); i++) {
			BdvSourceTable.getColumnModel().getColumn(i).setPreferredWidth(75);
		}
		
		JScrollPane scrollPane = new JScrollPane(BdvSourceTable);
		
		//Dimension dim = new Dimension(DataTable.getColumnCount()*75 + 5, 500);
		Dimension dim = new Dimension(500, 500);
		
		scrollPane.setMinimumSize(dim);
		scrollPane.setMaximumSize(dim);
		scrollPane.setPreferredSize(dim);
		
		BdvSourcePanel.add(scrollPane, BorderLayout.CENTER);
		
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new GridBagLayout());
		GridBagConstraints BdvSourcePanelGBC = new GridBagConstraints();
		BdvSourcePanelGBC.anchor = GridBagConstraints.NORTH;
		
		//Top, left, bottom, right
		BdvSourcePanelGBC.insets = new Insets(5, 0, 5, 0);
		
		BdvSourcePanelGBC.weightx = 1;
		BdvSourcePanelGBC.weighty = 1;
		
		BdvSourcePanelGBC.gridx = 0;
		BdvSourcePanelGBC.gridy = 0;
		
		JPanel AddPanel = new JPanel();
		AddPanel.setLayout(new BorderLayout());
		JTextField newSource = new JTextField(12);
		Dimension dimParm = new Dimension(200, 20);
		newSource.setMinimumSize(dimParm);
		AddPanel.add(newSource, BorderLayout.CENTER);
		JButton Add = new JButton("Add");
		Add.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!newSource.getText().equals("") && archive.getNumberOfImageMetadataRecords() != 0) {
					imageMetadataRecordChanged = true;
					File file = uiService.chooseFile(archive.getFile(), FileWidget.OPEN_STYLE);
					MarsBdvSource source = new MarsBdvSource(newSource.getText().trim());
					source.setPathToXml(file.getAbsolutePath());
					imageMetadata.putBdvSource(source);
					updateBdvSourceList();
					BdvSourceTableModel.fireTableDataChanged();
				}
			}
		});
		AddPanel.add(Add, BorderLayout.EAST);
		
		southPanel.add(AddPanel, BdvSourcePanelGBC);
		
		JButton Remove = new JButton("Remove");
		Remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (BdvSourceTable.getSelectedRow() != -1 && archive.getNumberOfImageMetadataRecords() != 0) {
					imageMetadataRecordChanged = true;
					String SourceName = (String)BdvSourceTable.getValueAt(BdvSourceTable.getSelectedRow(), 0);
					imageMetadata.removeBdvSource(SourceName);
					updateBdvSourceList();
					BdvSourceTableModel.fireTableDataChanged();
				}
			}
		});
		
		BdvSourcePanelGBC.gridy += 1;
		BdvSourcePanelGBC.anchor = GridBagConstraints.NORTHEAST;
		southPanel.add(Remove, BdvSourcePanelGBC);
		
		BdvSourcePanel.add(southPanel, BorderLayout.SOUTH);
		
		return BdvSourcePanel;
	}
	
	public JScrollPane buildMetadataProperties() {
		JPanel pane = new JPanel();
		
		pane.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc = new GridBagConstraints();
		
		gbc.anchor = GridBagConstraints.NORTHWEST;
		
		gbc.weightx = 1;
		gbc.weighty = 1;
		
		gbc.gridx = 0;
		gbc.gridy = 0;
		
		gbc.insets = new Insets(10, 10, 10, 10);
		
		//UID and image UID for this molecule
		JLabel uidName = new JLabel("UID");
		uidName.setFont(new Font("Menlo", Font.BOLD, 12));
        pane.add(uidName, gbc);
		
        gbc.gridy += 1;
		UIDLabel = new JTextField("" + imageMetadata.getUID());
		UIDLabel.setFont(new Font("Menlo", Font.PLAIN, 12));
		UIDLabel.setEditable(false);
		UIDLabel.setBackground(null);
		pane.add(UIDLabel, gbc);
		
		gbc.gridy += 1;
		JLabel microscope = new JLabel("Microscope");
		microscope.setFont(new Font("Menlo", Font.BOLD, 12));
		pane.add(microscope, gbc);
		
		gbc.gridy += 1;
		Microscope = new JTextField("" + imageMetadata.getMicroscopeName());
		Microscope.setFont(new Font("Menlo", Font.PLAIN, 12));
		Microscope.setEditable(false);
		Microscope.setBackground(null);
		pane.add(Microscope, gbc);
		
		gbc.gridy += 1;
		JLabel CollectionDate = new JLabel("Collection Date");
		CollectionDate.setFont(new Font("Menlo", Font.BOLD, 12));
		pane.add(CollectionDate, gbc);
		
		gbc.gridy += 1;
		DateLabel = new JTextField("" + imageMetadata.getCollectionDate());
		DateLabel.setFont(new Font("Menlo", Font.PLAIN, 12));
		DateLabel.setEditable(false);
		DateLabel.setBackground(null);
		pane.add(DateLabel, gbc);
		
		gbc.gridy += 1;
		JLabel SourceFolder = new JLabel("Source Path");
		SourceFolder.setFont(new Font("Menlo", Font.BOLD, 12));
		pane.add(SourceFolder, gbc);
		
		gbc.gridy += 1;
		SourcePath = new JTextField("" + imageMetadata.getSourceDirectory());
		SourcePath.setFont(new Font("Menlo", Font.PLAIN, 12));
		SourcePath.setEditable(false);
		SourcePath.setBackground(null);
		pane.add(SourcePath, gbc);
		
		GridBagConstraints northGBC = new GridBagConstraints();
		northGBC.anchor = GridBagConstraints.NORTHWEST;
		
		northGBC.weightx = 1;
		northGBC.weighty = 1;
		
		northGBC.gridx = 0;
		northGBC.gridy = 0;
		
		JPanel pane2 = new JPanel();
		pane2.setLayout(new GridBagLayout());
		
		pane2.add(pane, northGBC);
		
		JScrollPane scrollpane = new JScrollPane(pane2);
		return scrollpane;
	}
	
	private JScrollPane buildMetadataTable() {
		DataTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0)
					return rowIndex + 1;
				
				return imageMetadata.getDataTable().get(columnIndex - 1, rowIndex);
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0)
					return "Row";
				
				return imageMetadata.getDataTable().getColumnHeader(columnIndex - 1);
			}

			@Override
			public int getRowCount() {
				return imageMetadata.getDataTable().getRowCount();
			}
			
			@Override
			public int getColumnCount() {
				return imageMetadata.getDataTable().getColumnCount() + 1;
			}
			
			/*
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				if (imageMetadata.getDataTable().get(columnIndex - 1)  instanceof DoubleColumn) {
					imageMetadata.getDataTable().set(columnIndex - 1, rowIndex, Double.valueOf((String)aValue));
				} else {
					//Otherwise we just put a String
					imageMetadata.getDataTable().set(columnIndex - 1, rowIndex, (String)aValue);
				}
			}
			*/
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
		
		for (int i = 0; i < DataTable.getColumnCount(); i++) {
			DataTable.getColumnModel().getColumn(i).sizeWidthToFit();
		}
		
		JScrollPane scrollPane = new JScrollPane(DataTable);
		
		//Dimension dim = new Dimension(DataTable.getColumnCount()*75 + 5, 500);
		Dimension dim = new Dimension(500, 500);
		
		scrollPane.setMinimumSize(dim);
		scrollPane.setMaximumSize(dim);
		scrollPane.setPreferredSize(dim);
		
		return scrollPane;
	}

	private JScrollPane makeLogTab() {
		log = new JTextArea(imageMetadata.getLog());
		log.setFont(new Font("Menlo", Font.PLAIN, 12));
		log.setEditable(false);
        JScrollPane pane = new JScrollPane(log);
		return pane;
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
        
        globalPan.add(southPan, BorderLayout.SOUTH);
		
		return globalPan;
	}

	public void updateParameterList() {
		ParameterList = new String[imageMetadata.getParameters().keySet().size()];
		imageMetadata.getParameters().keySet().toArray(ParameterList);
	}
	
	public void updateTagList() {
		TagList = new String[imageMetadata.getTags().size()];
		imageMetadata.getTags().toArray(TagList);
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
				
				return imageMetadata.getParameters().get(ParameterList[rowIndex]);
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
				imageMetadataRecordChanged = true;
				double value = Double.parseDouble((String)aValue);
				imageMetadata.setParameter(ParameterList[rowIndex], value);
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
		
		//ParameterTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		
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
					imageMetadataRecordChanged = true;
					imageMetadata.setParameter(newParameter.getText().trim(), 0);
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
					imageMetadataRecordChanged = true;
					String param = (String)ParameterTable.getValueAt(ParameterTable.getSelectedRow(), 0);
					imageMetadata.removeParameter(param);
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
		
		//JLabel tagsName = new JLabel("Tags");
		//tagsName.setFont(new Font("Menlo", Font.BOLD, 12));
		//tagPanel.add(tagsName, tagPanelGBC);
		
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
					imageMetadataRecordChanged = true;
					imageMetadata.addTag(newTag.getText().trim());
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
					imageMetadataRecordChanged = true;
					imageMetadata.removeTag(tag);
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
	
	public void saveCurrentRecord() {
		if (archive.getNumberOfImageMetadataRecords() != 0)
			archive.putImageMetadata(imageMetadata);
	}
	
	public void updateAll() {
		if (archive.getNumberOfImageMetadataRecords() == 0) {
			imageMetadata = DummyImageMetadata;
			Notes.setEditable(false);
		} else if (archive.getImageMetadata(imageMetadata.getUID()) == null) {
			imageMetadata = archive.getImageMetadata(0);
			Notes.setEditable(true);
		} else {
			//Need to reload the current record if
			//working in virtual storage
			//This ensures if a command changed the values
			//The new values are loaded 
			//this prevents overwriting when switching records
			//in the window..
			imageMetadata = archive.getImageMetadata(imageMetadata.getUID());
			Notes.setEditable(true);
		}
		imageMetadataRecordChanged = false;
		
		//Update index table in case tags were changed
		if (imageMetadataCount < archive.getNumberOfImageMetadataRecords()) {
			imageMetadataIndexTableModel.fireTableRowsInserted(imageMetadataCount - 1, archive.getNumberOfImageMetadataRecords() - 1);
			imageMetadataCount = archive.getNumberOfImageMetadataRecords();
		} else if (imageMetadataCount > archive.getNumberOfImageMetadataRecords()) {
			imageMetadataIndexTableModel.fireTableRowsDeleted(archive.getNumberOfImageMetadataRecords() - 1, imageMetadataCount - 1);
			imageMetadataCount = archive.getNumberOfImageMetadataRecords();
		}
			
		//Update all entries...
		if (imageMetadataCount != 0) {
			imageMetadataIndexTableModel.fireTableRowsUpdated(0, imageMetadataCount - 1);
		}
		
		updateBdvSourceList();
		updateParameterList();
		updateTagList();
		
		//Update Labels
		UIDLabel.setText(imageMetadata.getUID());
		Microscope.setText(imageMetadata.getMicroscopeName());
		DateLabel.setText(imageMetadata.getCollectionDate());
		SourcePath.setText(imageMetadata.getSourceDirectory());
		
		//Update DataTable
		DataTableModel.fireTableStructureChanged();
		resizeColumnWidth(DataTable);
		for (int i = 0; i < DataTable.getColumnCount(); i++)
			DataTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
		//Update Parameter list
		ParameterTableModel.fireTableDataChanged();
		for (int i = 0; i < ParameterTable.getColumnCount(); i++)
			ParameterTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
		//Update Parameter list
		BdvSourceTableModel.fireTableDataChanged();
		for (int i = 0; i < BdvSourceTable.getColumnCount(); i++)
			BdvSourceTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
		//Update TagList
		TagTableModel.fireTableDataChanged();
		for (int i = 0; i < TagTable.getColumnCount(); i++)
			TagTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
		//Update Comments
		Notes.setText(imageMetadata.getNotes());
				
		//Update Log
		log.setText(imageMetadata.getLog());
		
	}
	
	private void filterImageMetadataIndex() {
        RowFilter<AbstractTableModel, Object> rf = null;
        //If current expression doesn't parse, don't update.
        try {
        	String searchString = imageMetadataSearchField.getText();
        	
        	if (searchString.contains(",")) {
	        	String[] searchlist = searchString.split(",");
	            for (int i=0; i<searchlist.length; i++) {
	            	searchlist[i] = searchlist[i].trim();
	            }
	        
	            searchString = "";
	            for (int i=0; i<searchlist.length; i++) {
	            	searchString += "(?=.*?(" + searchlist[i] + "))";
	            }
        	}
        	
            rf = RowFilter.regexFilter(searchString, 0, 1, 2);
        } catch (java.util.regex.PatternSyntaxException e) {
            return;
        }
        imageMetadataSorter.setRowFilter(rf);
        imageMetadataIndex.updateUI();
    }
	
	//getters and setters
	public void setImageMetadata(MarsImageMetadata imageMetadata) {
		this.imageMetadata = imageMetadata;
	}
	
	public MarsImageMetadata getImageMetadata() {
		return imageMetadata;
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
}
