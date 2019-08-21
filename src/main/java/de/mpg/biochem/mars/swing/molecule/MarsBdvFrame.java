package de.mpg.biochem.mars.swing.molecule;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import bdv.SpimSource;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.util.Affine3DHelpers;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandlePanel;
import bdv.util.BdvStackSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.ViewerState;
import mpicbg.spim.data.SpimDataException;
import de.mpg.biochem.mars.molecule.*;
import ij.ImagePlus;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;
import mpicbg.spim.data.registration.*;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;

public class MarsBdvFrame {
	
	private final JFrame frame;
	
	private JTextField scaleField;
	private JCheckBox autoUpdate;
	
	private HashMap<String, ArrayList<SpimDataMinimal>> bdvSources;
	
	private String metaUID;
	
	private BdvHandlePanel bdv;
	private List<BdvStackSource<?>> BDVs;
	
	private String xParameter, yParameter;
	private MoleculeArchive<?,?,?> archive;
	private MoleculePanel molPane;
	
	protected AffineTransform3D viewerTransform;
	
	public MarsBdvFrame(MoleculeArchive<?,?,?> archive, MoleculePanel molPane, String xParameter, String yParameter) {
		this.archive = archive;
		this.xParameter = xParameter;
		this.yParameter = yParameter;
		this.molPane = molPane;
		
		bdvSources = new HashMap<String, ArrayList<SpimDataMinimal>>();
		
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		
		frame = new JFrame( archive.getName() + " Bdv" );
		
		JPanel buttonPane = new JPanel();
		
		JButton reload = new JButton("Reload");
		reload.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				load();
			}
		});
		buttonPane.add(reload);
		
		JButton resetView = new JButton("Reset view");
		resetView.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				resetView();
			}
		});
		buttonPane.add(resetView);
		
		JButton goTo = new JButton("Go to molecule");
		goTo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Molecule molecule = molPane.getMolecule();
				if (molecule != null && autoUpdate.isSelected()) {			
					MarsImageMetadata meta = archive.getImageMetadata(molecule.getImageMetadataUID());
					if (!metaUID.equals(meta.getUID())) {
						metaUID = meta.getUID();
						createView(meta);
					}
					if (molecule.hasParameter(xParameter) && molecule.hasParameter(yParameter))
						goTo(molecule.getParameter(xParameter), molecule.getParameter(yParameter));
				 }
			}
		});
		buttonPane.add(goTo);
		
		JPanel optionsPane = new JPanel();
		
		autoUpdate = new JCheckBox("Auto update", true);
		
		optionsPane.add(new JLabel("Zoom "));
		
		scaleField = new JTextField(6);
		scaleField.setText("10");
		Dimension dimScaleField = new Dimension(100, 20);
		scaleField.setMinimumSize(dimScaleField);
		
		optionsPane.add(scaleField);
		optionsPane.add(autoUpdate);
		
		bdv = new BdvHandlePanel( frame, Bdv.options().is2D() );
		frame.add( bdv.getViewerPanel(), BorderLayout.CENTER );
		
		JPanel panel = new JPanel(new GridLayout(2, 1));
		
		panel.add(buttonPane);
		panel.add(optionsPane);
		
		frame.add(panel, BorderLayout.SOUTH);
		frame.setPreferredSize( new Dimension( 800, 600 ) );
		frame.pack();
		frame.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
		
		load();
	}
	
	public void load() {
		MarsImageMetadata meta = archive.getImageMetadata(molPane.getMolecule().getImageMetadataUID());
		metaUID = meta.getUID();
		createView(meta);
		if (molPane.getMolecule().hasParameter(xParameter) && molPane.getMolecule().hasParameter(yParameter)) 
			goTo(molPane.getMolecule().getParameter(xParameter), molPane.getMolecule().getParameter(yParameter));
	}
	
	public void setMolecule(Molecule molecule) {
		if (molecule != null && autoUpdate.isSelected()) {			
			MarsImageMetadata meta = archive.getImageMetadata(molecule.getImageMetadataUID());
			if (!metaUID.equals(meta.getUID())) {
				metaUID = meta.getUID();
				createView(meta);
			}
			if (molecule.hasParameter(xParameter) && molecule.hasParameter(yParameter))
				goTo(molecule.getParameter(xParameter), molecule.getParameter(yParameter));
		 }
	}
	
	private void createView(MarsImageMetadata meta) {
		if (bdv != null) {
			frame.setVisible( false );
			frame.remove(bdv.getViewerPanel());
		}
		bdv = new BdvHandlePanel( frame, Bdv.options().is2D() );		
		frame.add( bdv.getViewerPanel(), BorderLayout.CENTER );
		frame.setVisible( true );
		
		//bdv.getViewerPanel().removeAllSources();
		/*
		if (BDVs != null) {
			System.out.println("BDVs " + BDVs.size());
			for (BdvStackSource<?> b : BDVs)
				b.removeFromBdv();
		}
		*/
		
		if (!bdvSources.containsKey(meta.getUID()))
			bdvSources.put(meta.getUID(), loadNewSources(meta));
		
		for (SpimDataMinimal spimData : bdvSources.get(meta.getUID()))
			BDVs = BdvFunctions.show( spimData, Bdv.options().addTo( bdv ) );
		
		InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewerPanel(), bdv.getSetupAssignments() );
	}
	
	public ImagePlus exportView(int x0, int y0, int width, int height) {
		ArrayList< RandomAccessibleInterval< ARGBType > > raiList = new ArrayList< RandomAccessibleInterval< ARGBType > >(); 
		ARGBType t = null;
		
		int numChannels = bdvSources.get(metaUID).size();
		
		int TOP_left_x0 = (int)molPane.getMolecule().getParameter(xParameter) - x0;
		int TOP_left_y0 = (int)molPane.getMolecule().getParameter(yParameter) - y0;

		for ( int i = 0; i < numChannels; i++ ) {

			RealRandomAccessible< ARGBType > convertedSource;
			
			SpimSource spimS = new SpimSource( bdvSources.get(metaUID).get(i), 0, "source " + i );

			
			//t, level, interpolation
			convertedSource = ( RealRandomAccessible< ARGBType > ) spimS.getInterpolatedSource( 0, 0, Interpolation.NLINEAR );
			
			final RealRandomAccessible< ARGBType > raiRaw = ( RealRandomAccessible< ARGBType > )spimS.getInterpolatedSource( 0, 0, Interpolation.NLINEAR );

			// apply the transformations
			// AffineTransform3D tmpAffine = new AffineTransform3D();
			//final AffineRandomAccessible< ARGBType, AffineGet > rai = RealViews.affine( 
			//		raiRaw, pixelRenderToPhysical.inverse() );
			
			raiList.add( Views.interval( Views.raster( raiRaw ), new long[] { TOP_left_x0, TOP_left_y0 }, new long[]{ TOP_left_x0 + width, TOP_left_y0 + height } ) );
		}
		
		RandomAccessibleInterval< ARGBType > raiStack = Views.stack( raiList );
		
		ImagePlus ip = null;
		ip = ImageJFunctions.wrap( raiStack, "warped_moving_image" );
		
		
		/*
		if ( isVirtual )
		{
			ip = ImageJFunctions.wrap( raiStack, "warped_moving_image" );
		}
		else if( nThreads == 1 )
		{
			ip = copyToImageStack( raiStack, raiStack );
		}
		else
		{
			System.out.println( "render with " + nThreads + " threads.");
			final ImagePlusImgFactory< ARGBType > factory = new ImagePlusImgFactory< ARGBType >( new ARGBType() );

			if ( outputInterval.numDimensions() == 3 )
			{
				// A bit of hacking to make slices the 4th dimension and
				// channels the 3rd since that's how ImagePlusImgFactory does it
				final long[] dimensions = new long[ 4 ];
				dimensions[ 0 ] = outputInterval.dimension( 0 );	// x
				dimensions[ 1 ] = outputInterval.dimension( 1 );	// y
				dimensions[ 2 ] = numChannels; 					// c
				dimensions[ 3 ] = outputInterval.dimension( 2 ); 	// z 
				FinalInterval destIntervalPerm = new FinalInterval( dimensions );
				RandomAccessibleInterval< ARGBType > img = copyToImageStack( 
						raiStack,
						destIntervalPerm, factory, nThreads );
				ip = ((ImagePlusImg<ARGBType,?>)img).getImagePlus();
			}
			else if ( outputInterval.numDimensions() == 2 )
			{
				final long[] dimensions = new long[ 4 ];
				dimensions[ 0 ] = outputInterval.dimension( 0 );	// x
				dimensions[ 1 ] = outputInterval.dimension( 1 );	// y
				dimensions[ 2 ] = numChannels; 					// c
				dimensions[ 3 ] = 1; 							// z 
				FinalInterval destIntervalPerm = new FinalInterval( dimensions );
				RandomAccessibleInterval< ARGBType > img = copyToImageStack( 
						Views.addDimension( Views.extendMirrorDouble( raiStack )),
						destIntervalPerm, factory, nThreads );
				ip = ((ImagePlusImg<ARGBType,?>)img).getImagePlus();
			}
		}

		ip.getCalibration().pixelWidth = voxdim.dimension( 0 );
		ip.getCalibration().pixelHeight = voxdim.dimension( 1 );
		ip.getCalibration().pixelDepth = voxdim.dimension( 2 );
		ip.getCalibration().setUnit( voxdim.unit() );
		
		if( offsetTransform != null )
		{
			ip.getCalibration().xOrigin = offsetTransform.get( 0, 0 );
			ip.getCalibration().yOrigin = offsetTransform.get( 1, 1 );
			ip.getCalibration().zOrigin = offsetTransform.get( 2, 2 );
		}
		
		ip.setTitle( sources.get( movingSourceIndexList[ 0 ]).getSpimSource().getName() );
		*/
		return ip;
	}
	
	private ArrayList<SpimDataMinimal> loadNewSources(MarsImageMetadata meta) {
		ArrayList<SpimDataMinimal> spimArray = new ArrayList<SpimDataMinimal>();
		for (MarsBdvSource source : meta.getBdvSources()) {
			SpimDataMinimal spimData;
			try {
				spimData = new XmlIoSpimDataMinimal().load( source.getPathToXml() );
				
				//Add transforms to spimData...
				Map< ViewId, ViewRegistration > registrations = spimData.getViewRegistrations().getViewRegistrations();
				
				boolean driftCorrect = false;
				if (meta.getDataTable().hasColumn(source.getXDriftColumn()) && meta.getDataTable().hasColumn(source.getYDriftColumn())) {
					driftCorrect = true;
					System.out.println("found drift columns for " + source.getName());
				}
					
				for (ViewId id : registrations.keySet()) {
					if (driftCorrect) {
						double dX = meta.getDataTable().getValue(source.getXDriftColumn(), id.getTimePointId());
						double dY = meta.getDataTable().getValue(source.getYDriftColumn(), id.getTimePointId());
						registrations.get(id).getModel().set(source.getAffineTransform3D(dX, dY));
					} else
						registrations.get(id).getModel().set(source.getAffineTransform3D());
				}
				
				spimArray.add(spimData);
			} catch (SpimDataException e) {
				e.printStackTrace();
			}
		}
		
		return spimArray;
	}
	
	public void updateSetting(String xParameter, String yParameter) {
		this.xParameter = xParameter;
		this.yParameter = yParameter;
	}

	public void goTo(double x, double y) {
		resetView();
		
		Dimension dim = bdv.getBdvHandle().getViewerPanel().getDisplay().getSize();
		viewerTransform = bdv.getViewerPanel().getDisplay().getTransformEventHandler().getTransform();
		AffineTransform3D affine = viewerTransform;
		
		double[] source = new double[3];
		source[0] = 0;
		source[1] = 0;
		source[2] = 0;
		double[] target = new double[3];
		target[0] = 0;
		target[1] = 0;
		target[2] = 0;
		
		viewerTransform.apply(source, target);
		
		affine.set( affine.get( 0, 3 ) - target[0], 0, 3 );
		affine.set( affine.get( 1, 3 ) - target[1], 1, 3 );

		double scale = Double.valueOf(scaleField.getText());
		
		//check it was set correctly?
		
		// scale
		affine.scale( scale );
		
		source[0] = x;
		source[1] = y;
		source[2] = 0;
		
		affine.apply(source, target);

		affine.set( affine.get( 0, 3 ) - target[0] + dim.getWidth()/2, 0, 3 );
		affine.set( affine.get( 1, 3 ) - target[1] + dim.getHeight()/2, 1, 3 );
		
		bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( affine );
	}
	
	public void resetView() {
		ViewerPanel viewer = bdv.getBdvHandle().getViewerPanel();
		Dimension dim = viewer.getDisplay().getSize();
		viewerTransform = initTransform( (int)dim.getWidth(), (int)dim.getHeight(), false, viewer.getState() );
		viewer.setCurrentViewerTransform(viewerTransform);
	}

	/**
	 * Get a "good" initial viewer transform. The viewer transform is chosen
	 * such that for the first source,
	 * <ul>
	 * <li>the XY plane is aligned with the screen plane,
	 * at z = 0
	 * <li>centered and scaled such that the full <em>dim_x</em> by
	 * <em>dim_y</em> is visible.
	 * </ul>
	 *
	 * @param viewerWidth
	 *            width of the viewer display
	 * @param viewerHeight
	 *            height of the viewer display
	 * @param state
	 *            the {@link ViewerState} containing at least one source.
	 * @return proposed initial viewer transform.
	 */
	public static AffineTransform3D initTransform( final int viewerWidth, final int viewerHeight, final boolean zoomedIn, final ViewerState state ) {
		final int cX = viewerWidth / 2;
		final int cY = viewerHeight / 2;

		final Source< ? > source = state.getSources().get( state.getCurrentSource() ).getSpimSource();
		final int timepoint = state.getCurrentTimepoint();
		if ( !source.isPresent( timepoint ) )
			return new AffineTransform3D();

		final AffineTransform3D sourceTransform = new AffineTransform3D();
		source.getSourceTransform( timepoint, 0, sourceTransform );

		final Interval sourceInterval = source.getSource( timepoint, 0 );
		final double sX0 = sourceInterval.min( 0 );
		final double sX1 = sourceInterval.max( 0 );
		final double sY0 = sourceInterval.min( 1 );
		final double sY1 = sourceInterval.max( 1 );
		//final double sZ0 = sourceInterval.min( 2 );
		//final double sZ1 = sourceInterval.max( 2 );
		final double sX = ( sX0 + sX1 + 1 ) / 2;
		final double sY = ( sY0 + sY1 + 1 ) / 2;
		final double sZ = 0;//( sZ0 + sZ1 + 1 ) / 2;

		final double[][] m = new double[ 3 ][ 4 ];

		// rotation
		final double[] qSource = new double[ 4 ];
		final double[] qViewer = new double[ 4 ];
		Affine3DHelpers.extractApproximateRotationAffine( sourceTransform, qSource, 2 );
		LinAlgHelpers.quaternionInvert( qSource, qViewer );
		LinAlgHelpers.quaternionToR( qViewer, m );

		// translation
		final double[] centerSource = new double[] { sX, sY, sZ };
		final double[] centerGlobal = new double[ 3 ];
		final double[] translation = new double[ 3 ];
		sourceTransform.apply( centerSource, centerGlobal );
		LinAlgHelpers.quaternionApply( qViewer, centerGlobal, translation );
		LinAlgHelpers.scale( translation, -1, translation );
		LinAlgHelpers.setCol( 3, translation, m );

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewerTransform.set( m );

		// scale
		final double[] pSource = new double[] { sX1 + 0.5, sY1 + 0.5, sZ };
		final double[] pGlobal = new double[ 3 ];
		final double[] pScreen = new double[ 3 ];
		sourceTransform.apply( pSource, pGlobal );
		viewerTransform.apply( pGlobal, pScreen );
		final double scaleX = cX / pScreen[ 0 ];
		final double scaleY = cY / pScreen[ 1 ];
		final double scale;
		if ( zoomedIn )
			scale = Math.max( scaleX, scaleY );
		else
			scale = Math.min( scaleX, scaleY );
		viewerTransform.scale( scale );

		// window center offset
		viewerTransform.set( viewerTransform.get( 0, 3 ) + cX, 0, 3 );
		viewerTransform.set( viewerTransform.get( 1, 3 ) + cY, 1, 3 );
		return viewerTransform;
	}
}
