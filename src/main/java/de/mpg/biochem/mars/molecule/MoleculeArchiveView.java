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

import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.ui.UserInterface;
import org.scijava.ui.viewer.AbstractDisplayViewer;
import org.scijava.ui.viewer.DisplayViewer;

import net.imagej.display.WindowService;

@Plugin(type = DisplayViewer.class)
public class MoleculeArchiveView extends AbstractDisplayViewer<MoleculeArchive> implements DisplayViewer<MoleculeArchive> {
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	//This method is called to create and display a window
	//here we override it to make sure that calls like uiService.show( .. for MoleculeArchive 
	//will use this method automatically..
	@Override
	public void view(final UserInterface ui, final Display<?> d) {	
		MoleculeArchive archive = (MoleculeArchive)d.get(0);
		archive.setName(d.getName());

		moleculeArchiveService.addArchive(archive);
		d.setName(archive.getName());
		
		new MoleculeArchiveWindow(archive, moleculeArchiveService);
	}

	@Override
	public boolean canView(final Display<?> d) {
		if (d instanceof MoleculeArchiveDisplay) {
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public MoleculeArchiveDisplay getDisplay() {
		return (MoleculeArchiveDisplay) super.getDisplay();
	}

	@Override
	public boolean isCompatible(UserInterface arg0) {
		//Needs to be updated if all contexts are to be enabled beyond ImageJ
		return true;
	}
}