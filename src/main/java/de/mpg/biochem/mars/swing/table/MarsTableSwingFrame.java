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
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package de.mpg.biochem.mars.swing.table;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import ij.gui.GenericDialog;
import ij.WindowManager;
//import org.scijava.widget.FileWidget;

import ij.io.SaveDialog;

import org.scijava.plugin.Parameter;
import org.scijava.table.*;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.swing.plot.*;
import de.mpg.biochem.mars.table.*;

public class MarsTableSwingFrame implements ActionListener, MarsTableWindow {
	MarsTable results;
	
	@Parameter
	MarsTableService marsTableService;
	
    @Parameter
    private UIService uiService;
	
	private JFrame frame;
	JTable table;
	JScrollPane scrollPane;
	private AbstractTableModel tableModel;
	private JMenuItem saveAsMenuItem = new JMenuItem("Save As", KeyEvent.VK_S);
	private JMenuItem exportToJSONMenuItem = new JMenuItem("Export to JSON", KeyEvent.VK_E);
	private JMenuItem renameMenuItem = new JMenuItem("Rename", KeyEvent.VK_R);
	private JMenuItem copyMenuItem = new JMenuItem("Copy", KeyEvent.VK_C);
	private JMenuItem clearMenuItem = new JMenuItem("Clear");
	private JMenuItem selectAllMenuItem = new JMenuItem("Select All", KeyEvent.VK_A);
	
	private JMenuItem singleCurveMenuItem = new JMenuItem("Single Curve");
	//private JMenuItem multiCurveMenuItem = new JMenuItem("Multiple Curves");
	//private JMenuItem multiPlotMenuItem = new JMenuItem("Multiple Plots");
	
	//private JMenuItem plotMenuItem = new JMenuItem("Plot", KeyEvent.VK_P);
	
	//static so that table locations are offset...
	static int pos_x = 100;
	static int pos_y = 130;
	static int offsetX = 0;
	
	public MarsTableSwingFrame(String name, MarsTable results, MarsTableService marsTableService) {
		this.results = results;
		this.marsTableService = marsTableService;
		this.uiService = marsTableService.getUIService();
		results.setWindow(this);
		createFrame(name);
		
		// add window to window manager
		// IJ1 style IJ2 doesn't seem to work...
		if (!uiService.isHeadless())
			WindowManager.addWindow(frame);
	}
	
	public void update() {
		for (int i = 0; i < table.getColumnCount(); i++)
			table.getColumnModel().getColumn(i).setPreferredWidth(75);
		
		tableModel.fireTableStructureChanged();
	}
	
	private void createFrame(String name) {
		tableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0)
					return rowIndex + 1;
				
				return results.get(columnIndex - 1, rowIndex);
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0)
					return "Row";
				
				return results.getColumnHeader(columnIndex - 1);
			}

			@Override
			public int getRowCount() {
				return results.getRowCount();
			}
			
			@Override
			public int getColumnCount() {
				return results.getColumnCount() + 1;
			}
			
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				if (results.get(columnIndex - 1)  instanceof DoubleColumn) {
					results.set(columnIndex - 1, rowIndex, Double.valueOf((String)aValue));
				} else {
					//Otherwise we just put a String
					results.set(columnIndex - 1, rowIndex, (String)aValue);
				}
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return columnIndex > 0;
			}
			
		};
		
		table = new JTable(tableModel);
		table.setRowSelectionAllowed(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		
		for (int i = 0; i < table.getColumnCount(); i++)
			table.getColumnModel().getColumn(i).setPreferredWidth(75);
		
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		scrollPane = new JScrollPane(table);
		JMenuBar mb = new JMenuBar();
		
		// file menu
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		
		fileMenu.add(saveAsMenuItem);
		fileMenu.add(exportToJSONMenuItem);
		fileMenu.add(renameMenuItem);
		
		mb.add(fileMenu);
		
		// edit menu
		JMenu editMenu = new JMenu("Edit");
		editMenu.setMnemonic(KeyEvent.VK_E);
		
		editMenu.add(copyMenuItem);
		editMenu.add(clearMenuItem);
		editMenu.add(selectAllMenuItem);
		mb.add(editMenu);
		
		JMenu plotMenu = new JMenu("Plot");
		mb.add(plotMenu);
		//toolsMenu.add(addMetaDataMenuItem);
		/*
		addMetaDataMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 //File imageFolder = uiService.chooseFile(archive.getFile(), FileWidget.DIRECTORY_STYLE);
	 			 //archive.addImageMetaData(new ImageMetaData(imageFolder, moleculeArchiveService, "Odin"));
	          }
	       });
	       */
		plotMenu.add(singleCurveMenuItem);
		singleCurveMenuItem.addActionListener(this);
		
		//plotMenu.add(multiCurveMenuItem);
		//multiCurveMenuItem.addActionListener(this);
		
		//plotMenu.add(multiPlotMenuItem);
		//multiPlotMenuItem.addActionListener(this);

		// set action listeners
		saveAsMenuItem.addActionListener(this);
		exportToJSONMenuItem.addActionListener(this);
		renameMenuItem.addActionListener(this);
		
		copyMenuItem.addActionListener(this);
		clearMenuItem.addActionListener(this);
		selectAllMenuItem.addActionListener(this);

		//plotMenuItem.addActionListener(this);
		
		frame = new JFrame(name);
		frame.setSize(400, 300);
		frame.setLocation(pos_x, pos_y);
		pos_x += 10;
 		pos_y += 30;
 		if (pos_y > 600) {
 			offsetX += 200;
 			pos_x = offsetX;
 			pos_y = 130;
 		} else if (pos_x > 1000) {
 			offsetX = 0;
 			pos_x = 100;
 			pos_y = 130;
 		}
 		frame.addWindowListener(new WindowAdapter()
 	      {
 	         public void windowClosing(WindowEvent e)
 	         {
 	           close();
 	         }
 	      });
		frame.setLayout(new BorderLayout());
		frame.add(scrollPane);
		frame.setJMenuBar(mb);
		frame.setVisible(true);
	}
	
	public MarsTable getResults() {
		return results;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == renameMenuItem) {
			String name = JOptionPane.showInputDialog("Table name", frame.getTitle());
			rename(name);
		} else if (e.getSource() == clearMenuItem) {
			deleteRows();
		} else if (e.getSource() == selectAllMenuItem) {
			selectAll();
		} else if (e.getSource() == saveAsMenuItem) {
			saveAs();
		} else if (e.getSource() == exportToJSONMenuItem) {
			exportToJSON();
		} else if (e.getSource() == singleCurveMenuItem) {
			PlotDialog dialog = new PlotDialog("Curve Plot", results, 1, true);
			dialog.showDialog();
        	if (dialog.wasCanceled())
     			return;

        	dialog.update(dialog);
        	
    		PlotData curve1 = new PlotData(results, dialog.getXColumnName(), dialog.getNextYColumnName(), dialog.getNextCurveColor(), dialog.getGroupColumn(), dialog.getCurveType(), results.getName());
    		
    		ArrayList<PlotData> plot_data = new ArrayList<PlotData>();
    		plot_data.add(curve1);
    		
    		new ResultsPlotter(plot_data, results.getName(), dialog.getGroupColumn());
		}
	}
	protected boolean saveAs() {
		//String filename = frame.getTitle();
		//if (!filename.endsWith(".csv") && !filename.endsWith(".CSV")) {
		//	filename += ".csv";
		//}
		
		//This is not working properly in the current ImageJ API, I am not sure why
		//File file = new File(filename);
		//file = marsTableService.getUIService().chooseFile(file, FileWidget.SAVE_STYLE);
		
		SaveDialog sd = new SaveDialog("Save Table", frame.getTitle(), ".csv");
        String file = sd.getFileName();
        if (file==null) return false;
        String path = sd.getDirectory() + file;
		try {
			results.saveAs(path);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}
	protected boolean exportToJSON() {
		SaveDialog sd = new SaveDialog("Export to JSON", frame.getTitle(), ".json");
        String file = sd.getFileName();
        if (file==null) return false;
        String path = sd.getDirectory() + file;
		return results.saveAsJSON(path);
	}
	
	public void rename(String name) {
		if (name != null) {
			if (marsTableService.rename(frame.getTitle(), name)) {
				if (!uiService.isHeadless())
					WindowManager.removeWindow(frame);
				
				frame.setTitle(name);
				
				if (!uiService.isHeadless())
					WindowManager.addWindow(frame);
			}
		}
	}
	
	public void close() {
		marsTableService.removeResultsTable(results.getName());
		frame.setVisible(false);
		frame.dispose();
		
		results.clear();
		if (!uiService.isHeadless())
			WindowManager.removeWindow(frame);
	}
	
	protected void deleteRows() {
		results.deleteRows(table.getSelectedRows());
		
		table.clearSelection();
		tableModel.fireTableDataChanged();
	}
	
	protected void selectAll() {
		table.selectAll();
	}
}
