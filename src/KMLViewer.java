/*
 * Copyright (C) 2012 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.ogc.kml.KMLAbstractFeature;
import gov.nasa.worldwind.ogc.kml.KMLAbstractGeometry;
import gov.nasa.worldwind.ogc.kml.KMLDocument;
import gov.nasa.worldwind.ogc.kml.KMLFolder;
import gov.nasa.worldwind.ogc.kml.KMLPlacemark;
import gov.nasa.worldwind.ogc.kml.KMLPoint;
import gov.nasa.worldwind.ogc.kml.KMLRoot;
import gov.nasa.worldwind.ogc.kml.impl.KMLController;
import gov.nasa.worldwind.render.Offset;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.retrieve.RetrievalService;
import gov.nasa.worldwind.util.WWIO;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwind.util.layertree.KMLLayerTreeNode;
import gov.nasa.worldwind.util.layertree.KMLNetworkLinkTreeNode;
import gov.nasa.worldwind.util.layertree.LayerTree;
import gov.nasa.worldwindx.examples.ApplicationTemplate;
import gov.nasa.worldwindx.examples.kml.KMLApplicationController;
import gov.nasa.worldwindx.examples.util.BalloonController;
import gov.nasa.worldwindx.examples.util.HotSpotController;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.stream.XMLStreamException;

/**
 * Built from an example application that allows the user to import a KML or KMZ
 * file as a layer. The contents of the file are displayed in a feature tree.
 * Click on KML features in the tree to navigate the view to the feature.
 * Clicking on features on the globe will open an info balloon for the feature,
 * if the feature provides a description. Use the File menu to open a document
 * from a local file or from a URL.
 *
 * @author tag
 * @version $Id: KMLViewer.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class KMLViewer extends ApplicationTemplate {

	public static class AppFrame extends ApplicationTemplate.AppFrame {
		/**
		 * 
		 */
		private static final long serialVersionUID = 421169486087652615L;
		protected static LayerTree layerTree;
		protected RenderableLayer hiddenLayer;

		protected HotSpotController hotSpotController;
		protected KMLApplicationController kmlAppController;
		protected BalloonController balloonController;

		private int layerCount = 0;

		private static ArrayList<String> names = new ArrayList<>();

		public AppFrame() {
			super(true, false, false); // Don't include the layer panel; we're
										// using the on-screen layer tree.

			Globe lineBuilder = new Globe(this.getWwd(), null, null);
			this.getContentPane().add(
					new LinePanel(this.getWwd(), lineBuilder),
					BorderLayout.WEST);

			// Add the on-screen layer tree, refreshing model with the
			// WorldWindow's current layer list. We
			// intentionally refresh the tree's model before adding the layer
			// that contains the tree itself. This
			// prevents the tree's layer from being displayed in the tree
			// itself.
			this.layerTree = new LayerTree(new Offset(20d, 160d, AVKey.PIXELS,
					AVKey.INSET_PIXELS));

			// default set the bing layer to ON so that we can zoom in much
			// farther with detail
			this.getWwd().getModel().getLayers().getLayerByName("Bing Imagery")
					.setEnabled(true);

			this.layerTree.getModel().refresh(
					this.getWwd().getModel().getLayers());

			// Set up a layer to display the on-screen layer tree in the
			// WorldWindow. This layer is not displayed in
			// the layer tree's model. Doing so would enable the user to hide
			// the layer tree display with no way of
			// bringing it back.
			this.hiddenLayer = new RenderableLayer();
			this.hiddenLayer.addRenderable(this.layerTree);
			this.getWwd().getModel().getLayers().add(this.hiddenLayer);

			// Add a controller to handle input events on the layer selector and
			// on browser balloons.
			this.hotSpotController = new HotSpotController(this.getWwd());

			// Add a controller to handle common KML application events.
			this.kmlAppController = new KMLApplicationController(this.getWwd());

			// Add a controller to display balloons when placemarks are clicked.
			// We override the method addDocumentLayer
			// so that loading a KML document by clicking a KML balloon link
			// displays an entry in the on-screen layer
			// tree.
			this.balloonController = new BalloonController(this.getWwd()) {
				@Override
				protected void addDocumentLayer(KMLRoot document) {
					addKMLLayer(document);
				}
			};

			// Give the KML app controller a reference to the BalloonController
			// so that the app controller can open
			// KML feature balloons when feature's are selected in the on-screen
			// layer tree.
			this.kmlAppController.setBalloonController(balloonController);

			// Size the World Window to maximized
			this.setExtendedState(MAXIMIZED_BOTH);
			this.pack();
			WWUtil.alignComponent(null, this, AVKey.CENTER);

			makeMenu(this);

			// Set up to receive SSLHandshakeExceptions that occur during
			// resource retrieval.
			WorldWind.getRetrievalService().setSSLExceptionListener(
					new RetrievalService.SSLExceptionListener() {
						public void onException(Throwable e, String path) {
							System.out.println(path);
							System.out.println(e);
						}
					});
		}

		/**
		 * Adds the specified <code>kmlRoot</code> to this app frame's
		 * <code>WorldWindow</code> as a new <code>Layer</code>, and adds a new
		 * <code>KMLLayerTreeNode</code> for the <code>kmlRoot</code> to this
		 * app frame's on-screen layer tree.
		 * <p/>
		 * This expects the <code>kmlRoot</code>'s
		 * <code>AVKey.DISPLAY_NAME</code> field to contain a display name
		 * suitable for use as a layer name.
		 *
		 * @param kmlRoot
		 *            the KMLRoot to add a new layer for.
		 */
		protected void addKMLLayer(KMLRoot kmlRoot) {
			// Create a KMLController to adapt the KMLRoot to the World Wind
			// renderable interface.
			KMLController kmlController = new KMLController(kmlRoot);

			// Adds a new layer containing the KMLRoot to the end of the
			// WorldWindow's layer list. This
			// retrieves the layer name from the KMLRoot's DISPLAY_NAME field.
			RenderableLayer layer = new RenderableLayer();
			layerCount++;

			layer.setName((String) kmlRoot.getField(AVKey.DISPLAY_NAME) + ""
					+ layerCount);
			layer.setOpacity(.5);
			layer.addRenderable(kmlController);

			names.add((String) kmlRoot.getField(AVKey.DISPLAY_NAME) + ""
					+ layerCount);

			this.getWwd().getModel().getLayers().add(layer);

			KMLAbstractFeature feature = kmlController.getKmlRoot()
					.getFeature();
			List<KMLAbstractFeature> features = ((KMLDocument) feature)
					.getFeatures();

			for (int i = 0; i < features.size(); i++) {
				KMLAbstractFeature featureInner = features.get(i);
				List<KMLAbstractFeature> featuresInner = null;

				if (featureInner instanceof KMLFolder) {
					featuresInner = ((KMLFolder) featureInner).getFeatures();

					for (int z = 0; z < featuresInner.size(); z++) {
						KMLPlacemark k = (KMLPlacemark) featuresInner.get(z);
						KMLAbstractGeometry geom = ((KMLPlacemark) k)
								.getGeometry();
						if (geom instanceof KMLPoint) {
							System.out.println("Point placemark at: "
									+ ((KMLPoint) geom).getCoordinates());

							PointPlacemark incoming = new PointPlacemark(
									((KMLPoint) geom).getCoordinates());
							incoming.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
							incoming.setLabelText(k.getName());

							Globe.addToPlacemarkList(incoming);
						} else {
						}
					}
				}
			}

			// Adds a new layer tree node for the KMLRoot to the on-screen layer
			// tree, and makes the new node visible
			// in the tree. This also expands any tree paths that represent open
			// KML containers or open KML network
			// links.
			KMLLayerTreeNode layerNode = new KMLLayerTreeNode(layer, kmlRoot);
			this.layerTree.getModel().addLayer(layerNode);
			this.layerTree.makeVisible(layerNode.getPath());
			layerNode.expandOpenContainers(this.layerTree);

			// Listens to refresh property change events from KML network link
			// nodes. Upon receiving such an event this
			// expands any tree paths that represent open KML containers. When a
			// KML network link refreshes, its tree
			// node replaces its children with new nodes created from the
			// refreshed content, then sends a refresh
			// property change event through the layer tree. By expanding open
			// containers after a network link refresh,
			// we ensure that the network link tree view appearance is
			// consistent with the KML specification.
			layerNode.addPropertyChangeListener(
					AVKey.RETRIEVAL_STATE_SUCCESSFUL,
					new PropertyChangeListener() {
						public void propertyChange(
								final PropertyChangeEvent event) {
							if (event.getSource() instanceof KMLNetworkLinkTreeNode) {
								// Manipulate the tree on the EDT.
								SwingUtilities.invokeLater(new Runnable() {
									public void run() {
										((KMLNetworkLinkTreeNode) event
												.getSource())
												.expandOpenContainers(layerTree);
										getWwd().redraw();
									}
								});
							}
						}
					});
		}

		public static LayerTree getLayerTree() {
			return layerTree;
		}

	}

	// ===================== Control Panel ======================= //

	private static class LinePanel extends JPanel {
		/**
		 * 
		 */
		private static final long serialVersionUID = -1863858957421121530L;
		private final WorldWindow wwd;
		private final Globe lineBuilder;
		private JButton newButton;
		private JButton pauseButton;
		private JButton endButton;
		private JTextField latInputField = new JTextField("40.0619");

		private JTextField lonInputField = new JTextField("-74.5448");
		private JButton goNavButton;

		private JButton exportKMLButton;
		private JButton clearPoints;
		private JButton clearLastPoint;
		private JButton removeKML;

		private JPanel topPanel = new JPanel(new GridBagLayout());
		private GridBagConstraints c = new GridBagConstraints();
		private JPanel buttonPanel = new JPanel(new GridLayout(5, 1, 5, 5));

		private JPanel lowerPanel = new JPanel(new GridLayout(1, 1));
		private JPanel navPanel = new JPanel(new GridLayout(1, 3, 2, 0));
		private JPanel lowerButtPanel = new JPanel(new GridLayout(4, 1, 0, 5));

		private JSlider transp = new JSlider();

		private SpinnerModel startFrom;
		private JSpinner startFromSpinner;
		private JLabel startFromLabel = new JLabel(
				"<html><b>Start from: </b></html>");

		private JLabel prefix = new JLabel("<html><b>Prefix: </b></html>");
		private JTextField prefixInput = new JTextField("P");

		private JLabel nextPlacemark = new JLabel(
				"<html><b>Next placemark: </b></html>");
		private String placemarkName = "P";
		private int placemarkNumber = 1;
		private JLabel nextPlacemarkString = new JLabel(placemarkName + ""
				+ placemarkNumber);

		private JCheckBox append = new JCheckBox("Use Sequential");

		private static final int SPINNER_MAX = Integer.MAX_VALUE;

		public LinePanel(WorldWindow wwd, final Globe lineBuilder) {
			super(new BorderLayout());
			this.wwd = wwd;
			this.lineBuilder = lineBuilder;
			this.makePanel(new Dimension(200, 400));

			// add the same mouse listener but use the globe methods to interact
			wwd.getInputHandler().addMouseListener(new MouseAdapter() {

				public void mouseReleased(MouseEvent mouseEvent) {
					if (mouseEvent.getButton() == MouseEvent.BUTTON1) {
						if (!newButton.isEnabled()) {
							if (lineBuilder.isHasPostfix())
								nextPlacemarkString.setText(placemarkName + ""
										+ lineBuilder.getNumPoints());
							else {
								nextPlacemarkString.setText(placemarkName);

							}
						}

					}
				}
			});

		}

		private void makePanel(Dimension size) {

			c.fill = GridBagConstraints.HORIZONTAL;
			c.ipady = 10;
			c.ipadx = 10;

			topPanel.add(buttonPanel, c);
			buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

			c.gridy = 1;
			c.gridx = 0;

			topPanel.add(lowerButtPanel, c);
			lowerButtPanel.setBorder(BorderFactory
					.createEmptyBorder(5, 5, 5, 5));

			c.gridy = 2;
			c.gridx = 0;
			topPanel.add(navPanel, c);
			navPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));

			c.gridy = 3;
			c.gridx = 0;
			topPanel.add(lowerPanel, c);
			lowerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

			navPanel.add(latInputField);
			navPanel.add(lonInputField);
			goNavButton = new JButton("Go");
			goNavButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent actionEvent) {
					wwd.getView()
							.goTo(Position.fromDegrees(
									Double.parseDouble(latInputField.getText()),
									Double.parseDouble(lonInputField.getText()),
									200), 2000);

				}
			});
			navPanel.add(goNavButton);

			newButton = new JButton("Start");
			newButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent actionEvent) {
					lineBuilder.setArmed(true);
					pauseButton.setText("Pause");
					pauseButton.setEnabled(true);
					endButton.setEnabled(true);
					newButton.setEnabled(false);
					((Component) wwd).setCursor(Cursor
							.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
				}
			});
			buttonPanel.add(newButton);
			newButton.setEnabled(true);

			pauseButton = new JButton("Pause");
			pauseButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent actionEvent) {
					lineBuilder.setArmed(!lineBuilder.isArmed());
					pauseButton.setText(!lineBuilder.isArmed() ? "Resume"
							: "Pause");
					((Component) wwd).setCursor(Cursor.getDefaultCursor());
				}
			});
			pauseButton.setEnabled(false);

			exportKMLButton = new JButton("Export KML");
			exportKMLButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					CustomFileChooser cfc = new CustomFileChooser("kml");
					if (cfc.showSaveDialog(LinePanel.this) == CustomFileChooser.APPROVE_OPTION) {
						File file = cfc.getSelectedFile();

						lineBuilder.getAllPlacemarks();
						lineBuilder.exportKML(file.getAbsolutePath(),
								AppFrame.names);
					}

				}
			});
			lowerButtPanel.add(exportKMLButton);

			clearPoints = new JButton("Clear All Points");
			clearPoints.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					int reply = JOptionPane.showConfirmDialog(LinePanel.this,
							"Are you sure you want to clear all points?",
							"Clear All Points", JOptionPane.YES_NO_OPTION);

					if (reply == JOptionPane.YES_OPTION)
					{
						lineBuilder.clearPoints();
						placemarkNumber = lineBuilder.getNumPoints();
						placemarkName = prefixInput.getText();
						lineBuilder.setPrefix(placemarkName);
						if (lineBuilder.isHasPostfix())
							nextPlacemarkString.setText(placemarkName + ""
									+ placemarkNumber);
						else
							nextPlacemarkString.setText(placemarkName);
					}

					

				}
			});
			lowerButtPanel.add(clearPoints);

			clearLastPoint = new JButton("Clear Last Point");
			clearLastPoint.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					lineBuilder.clearLastPoint();
					placemarkNumber = lineBuilder.getNumPoints();
					placemarkName = prefixInput.getText();
					lineBuilder.setPrefix(placemarkName);
					if (lineBuilder.isHasPostfix())
						nextPlacemarkString.setText(placemarkName + ""
								+ placemarkNumber);
					else
						nextPlacemarkString.setText(placemarkName);

				}
			});
			lowerButtPanel.add(clearLastPoint);

			endButton = new JButton("Stop");
			endButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent actionEvent) {
					lineBuilder.setArmed(false);
					newButton.setEnabled(true);
					pauseButton.setEnabled(false);
					pauseButton.setText("Pause");
					endButton.setEnabled(false);
					((Component) wwd).setCursor(Cursor.getDefaultCursor());
				}
			});
			buttonPanel.add(endButton);
			endButton.setEnabled(false);

			startFrom = new SpinnerNumberModel(1, 1, SPINNER_MAX, 1);
			startFromSpinner = new JSpinner(startFrom);
			startFromLabel.setHorizontalAlignment(SwingConstants.CENTER);
			buttonPanel.add(startFromLabel);
			buttonPanel.add(startFromSpinner);

			prefix.setHorizontalAlignment(SwingConstants.CENTER);
			nextPlacemark.setHorizontalAlignment(SwingConstants.CENTER);

			buttonPanel.add(prefix);
			buttonPanel.add(prefixInput);
			buttonPanel.add(nextPlacemark);
			buttonPanel.add(nextPlacemarkString);

			append.setSelected(true);
			append.addItemListener(new ItemListener() {

				@Override
				public void itemStateChanged(ItemEvent e) {
					if (e.getStateChange() == ItemEvent.DESELECTED) {
						nextPlacemarkString.setText(placemarkName);
						lineBuilder.setHasPostfix(false);

					} else {
						placemarkNumber = lineBuilder.getNumPoints();
						nextPlacemarkString.setText(placemarkName + ""
								+ placemarkNumber);
						lineBuilder.setHasPostfix(true);
					}

				}
			});
			buttonPanel.add(append);

			startFrom.addChangeListener(new ChangeListener() {

				@Override
				public void stateChanged(ChangeEvent e) {
					SpinnerNumberModel mySpinner = (SpinnerNumberModel) (e
							.getSource());
					if ((int) mySpinner.getValue() < 1
							|| (int) mySpinner.getValue() > SPINNER_MAX) {

						mySpinner.setValue(1);
					} else {
						if (lineBuilder.isHasPostfix()) {
							lineBuilder.setNumPoints((int) mySpinner.getValue());
							placemarkNumber = (int) mySpinner.getValue();

							nextPlacemarkString.setText(placemarkName + ""
									+ placemarkNumber);
						} else {

							lineBuilder.setNumPoints((int) mySpinner.getValue());
							placemarkNumber = (int) mySpinner.getValue();

							nextPlacemarkString.setText(placemarkName);

						}

					}
				}
			});

			prefixInput.getDocument().addDocumentListener(
					new DocumentListener() {

						@Override
						public void removeUpdate(DocumentEvent e) {
							if (lineBuilder.isHasPostfix()) {
								placemarkNumber = lineBuilder.getNumPoints();
								placemarkName = prefixInput.getText();
								lineBuilder.setPrefix(placemarkName);
								nextPlacemarkString.setText(placemarkName + ""
										+ placemarkNumber);

							} else {
								placemarkNumber = lineBuilder.getNumPoints();
								placemarkName = prefixInput.getText();
								lineBuilder.setPrefix(placemarkName);
								nextPlacemarkString.setText(placemarkName);
							}

						}

						@Override
						public void insertUpdate(DocumentEvent e) {
							if (lineBuilder.isHasPostfix()) {
								placemarkNumber = lineBuilder.getNumPoints();
								placemarkName = prefixInput.getText();
								lineBuilder.setPrefix(placemarkName);
								nextPlacemarkString.setText(placemarkName + ""
										+ placemarkNumber);

							} else {
								placemarkNumber = lineBuilder.getNumPoints();
								placemarkName = prefixInput.getText();
								lineBuilder.setPrefix(placemarkName);
								nextPlacemarkString.setText(placemarkName);
							}

						}

						@Override
						public void changedUpdate(DocumentEvent e) {

						}
					});


			// This is the transparency slider and its options
			transp.setPaintTicks(true);
			transp.setMajorTickSpacing(50);
			transp.setPaintLabels(true);
			transp.addChangeListener(new ChangeListener() {

				@Override
				public void stateChanged(ChangeEvent e) {
					JSlider source = (JSlider) e.getSource();
					if (!source.getValueIsAdjusting()) {

						for (String name : AppFrame.names) {
							wwd.getModel()
									.getLayers()
									.getLayerByName(name)
									.setOpacity(
											(double) source.getValue() / 100);
						}

						wwd.redraw();
					}

				}
			});

			removeKML = new JButton("Remove Imported Layers");
			removeKML.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {

					for (String name : AppFrame.names) {
						// System.out.println(name);

						wwd.getModel().getLayers().getLayerByName(name)
								.dispose();

						AppFrame.getLayerTree().getModel()
								.refresh(wwd.getModel().getLayers());

					}

					wwd.redraw();

				}
			});
			lowerButtPanel.add(removeKML);

			lowerPanel.add(transp);

			buttonPanel.setBorder(new CompoundBorder(BorderFactory
					.createEmptyBorder(9, 9, 9, 9), new TitledBorder(
					"Sequential Placemarks")));
			lowerButtPanel
					.setBorder(new CompoundBorder(BorderFactory
							.createEmptyBorder(9, 9, 9, 9), new TitledBorder(
							"Options")));
			navPanel.setBorder(new CompoundBorder(BorderFactory
					.createEmptyBorder(9, 9, 9, 9), new TitledBorder(
					"Navigation (Latitude, Longitude)")));
			lowerPanel.setBorder(new CompoundBorder(BorderFactory
					.createEmptyBorder(9, 9, 9, 9), new TitledBorder(
					"Imported Layer Transparencies")));

			// Add the buttons, scroll bar and inner panel to a titled panel
			// that will resize with the main window.
			JPanel outerPanel = new JPanel(new BorderLayout());
			outerPanel.setBorder(new CompoundBorder(BorderFactory
					.createEmptyBorder(9, 9, 9, 9), new TitledBorder(
					"Placemark Digitizing")));
			outerPanel.add(topPanel, BorderLayout.NORTH);
			this.add(outerPanel, BorderLayout.CENTER);
		}

	}

	/**
	 * A <code>Thread</code> that loads a KML file and displays it in an
	 * <code>AppFrame</code>.
	 */
	public static class WorkerThread extends Thread {
		/**
		 * Indicates the source of the KML file loaded by this thread.
		 * Initialized during construction.
		 */
		protected Object kmlSource;
		/**
		 * Indicates the <code>AppFrame</code> the KML file content is displayed
		 * in. Initialized during construction.
		 */
		protected AppFrame appFrame;

		/**
		 * Creates a new worker thread from a specified <code>kmlSource</code>
		 * and <code>appFrame</code>.
		 *
		 * @param kmlSource
		 *            the source of the KML file to load. May be a {@link File},
		 *            a {@link URL}, or an {@link java.io.InputStream}, or a
		 *            {@link String} identifying a file path or URL.
		 * @param appFrame
		 *            the <code>AppFrame</code> in which to display the KML
		 *            source.
		 */
		public WorkerThread(Object kmlSource, AppFrame appFrame) {
			this.kmlSource = kmlSource;
			this.appFrame = appFrame;
		}

		/**
		 * Loads this worker thread's KML source into a new
		 * <code>{@link gov.nasa.worldwind.ogc.kml.KMLRoot}</code>, then adds
		 * the new <code>KMLRoot</code> to this worker thread's
		 * <code>AppFrame</code>. The <code>KMLRoot</code>'s
		 * <code>AVKey.DISPLAY_NAME</code> field contains a display name created
		 * from either the KML source or the KML root feature name.
		 * <p/>
		 * If loading the KML source fails, this prints the exception and its
		 * stack trace to the standard error stream, but otherwise does nothing.
		 */
		public void run() {
			try {
				KMLRoot kmlRoot = this.parse();

				// Set the document's display name
				kmlRoot.setField(AVKey.DISPLAY_NAME,
						formName(this.kmlSource, kmlRoot));

				// Schedule a task on the EDT to add the parsed document to a
				// layer
				final KMLRoot finalKMLRoot = kmlRoot;

				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						appFrame.addKMLLayer(finalKMLRoot);

					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * Parse the KML document.
		 *
		 * @return The parsed document.
		 *
		 * @throws IOException
		 *             if the document cannot be read.
		 * @throws XMLStreamException
		 *             if document cannot be parsed.
		 */
		protected KMLRoot parse() throws IOException, XMLStreamException {
			// KMLRoot.createAndParse will attempt to parse the document using a
			// namespace aware parser, but if that
			// fails due to a parsing error it will try again using a namespace
			// unaware parser. Note that this second
			// step may require the document to be read from the network again
			// if the kmlSource is a stream.
			return KMLRoot.createAndParse(this.kmlSource);
		}
	}

	protected static String formName(Object kmlSource, KMLRoot kmlRoot) {
		KMLAbstractFeature rootFeature = kmlRoot.getFeature();

		if (rootFeature != null && !WWUtil.isEmpty(rootFeature.getName()))
			return rootFeature.getName();

		if (kmlSource instanceof File)
			return ((File) kmlSource).getName();

		if (kmlSource instanceof URL)
			return ((URL) kmlSource).getPath();

		if (kmlSource instanceof String
				&& WWIO.makeURL((String) kmlSource) != null)
			return WWIO.makeURL((String) kmlSource).getPath();

		return "KML Layer";
	}

	/**
	 * @wbp.parser.entryPoint
	 */
	protected static void makeMenu(final AppFrame appFrame) {
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setMultiSelectionEnabled(true);
		fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(
				"KML/KMZ File", "kml", "kmz"));

		JMenuBar menuBar = new JMenuBar();
		appFrame.setJMenuBar(menuBar);
		JMenu fileMenu = new JMenu("File");
		JMenu helpMenu = new JMenu("Help");

		menuBar.add(fileMenu);

		JMenuItem openFileMenuItem = new JMenuItem(new AbstractAction(
				"Open File...") {
			/**
			 * 
			 */
			private static final long serialVersionUID = 2592899248183147945L;

			public void actionPerformed(ActionEvent actionEvent) {
				try {
					int status = fileChooser.showOpenDialog(appFrame);
					if (status == JFileChooser.APPROVE_OPTION) {
						for (File file : fileChooser.getSelectedFiles()) {
							new WorkerThread(file, appFrame).start();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		fileMenu.add(openFileMenuItem);

		JMenuItem openURLMenuItem = new JMenuItem(new AbstractAction(
				"Open URL...") {
			/**
			 * 
			 */
			private static final long serialVersionUID = -2826094156753749668L;

			public void actionPerformed(ActionEvent actionEvent) {
				try {
					String status = JOptionPane
							.showInputDialog(appFrame, "URL");
					if (!WWUtil.isEmpty(status)) {
						new WorkerThread(status.trim(), appFrame).start();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		fileMenu.add(openURLMenuItem);

		// Popup about message
		JMenuItem mntmAbout = new JMenuItem("About");
		mntmAbout.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent arg0) {

				JOptionPane
						.showMessageDialog(
								appFrame,
								"Placemark Digitizer v1.2\nDeveloped by Jack Jamieson 2015\nhttp://www.jackjamieson.me");

			}
		});
		helpMenu.add(mntmAbout);

		menuBar.add(helpMenu);
	}

	public static void main(String[] args) {
		try {
			// Set System L&F
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (UnsupportedLookAndFeelException e) {
			// handle exception
		} catch (ClassNotFoundException e) {
			// handle exception
		} catch (InstantiationException e) {
			// handle exception
		} catch (IllegalAccessException e) {
			// handle exception
		}

		// noinspection UnusedDeclaration
		@SuppressWarnings("unused")
		final AppFrame af = (AppFrame) start(
				"World Wind KML Placemark Digitizer", AppFrame.class);

	}

}
