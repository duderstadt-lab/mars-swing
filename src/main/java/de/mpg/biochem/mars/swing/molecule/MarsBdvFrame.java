package de.mpg.biochem.mars.swing.molecule;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.tools.brightness.SetupAssignments;
import bdv.util.AxisOrder;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandlePanel;
import bdv.util.BdvSource;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import de.mpg.biochem.mars.molecule.*;

import java.util.Random;

import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class MarsBdvFrame {
	
	private final JFrame frame;
	private final BdvHandlePanel bdv;
	
	private String xParameter, yParameter;
	private double scale;
	private MoleculeArchive<?,?,?> archive;
	
	protected final AffineTransform3D affine = new AffineTransform3D();
	
	public MarsBdvFrame(MoleculeArchive<?,?,?> archive, String xParameter, String yParameter, double scale) {
		this.archive = archive;
		this.xParameter = xParameter;
		this.yParameter = yParameter;
		this.scale = scale;
		
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		
		frame = new JFrame( archive.getName() + " Bdv" );
		bdv = new BdvHandlePanel( frame, Bdv.options().is2D() );
		frame.add( bdv.getViewerPanel(), BorderLayout.CENTER );
		frame.setPreferredSize( new Dimension( 300, 300 ) );
		frame.pack();
		frame.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
		frame.setVisible( true );
	}

	
	public void updateSetting(String xParameter, String yParameter, double scale) {
		this.xParameter = xParameter;
		this.yParameter = yParameter;
		this.scale = scale;
	}
	
	public void updateView( final double s, final double x, final double y ) {
		affine.set( affine.get( 0, 3 ) - x, 0, 3 );
		affine.set( affine.get( 1, 3 ) - y, 1, 3 );

		// scale
		affine.scale( s );

		// center un-shift
		affine.set( affine.get( 0, 3 ) + x, 0, 3 );
		affine.set( affine.get( 1, 3 ) + y, 1, 3 );
		
		//bdv.getBdvHandle().getViewerPanel().requestRepaint();
	}
	
	public void setMolecule(Molecule molecule) {
		if (molecule != null) {
     		//int xCenter = (int)(molecule.getParameter(xParameter) + 0.5);
     		//int yCenter = (int)(molecule.getParameter(yParameter) + 0.5);
			
			//Rectangle r = new Rectangle((int)(xCenter - width/2), (int)(yCenter - height/2), width, height);
			
			MarsImageMetadata meta = archive.getImageMetadata(molecule.getImageMetadataUID());
			
			
			for (String viewName : meta.getBdvViewList()) {
				SpimDataMinimal spimData;
				try {
					spimData = new XmlIoSpimDataMinimal().load( meta.getBdvView(viewName) );
					
					BdvFunctions.show( spimData, Bdv.options().addTo( bdv ) );
					
					affine.scale( 10 );
		
					bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform(affine);
					
					//updateView(10, 0, 0);
					
					//int s; // source index (channel, angle, ...)
					//int t; // timepoint index
					//RandomAccessibleInterval< ? > image = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader(s).getImage(t);
					
					//BdvFunctions.show( spimData );
					
					//bdv.getBdvHandle().getSetupAssignments().addSetup(setup);
					
					//ViewSetup
					
					//source.setDisplayRangeBounds( 0, 1255 );
					
					
				} catch (SpimDataException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		 }
	}
}
