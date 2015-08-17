/*
 * Copyright (C) 2012 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */


import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.ogc.collada.ColladaRoot;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.Polyline;
import gov.nasa.worldwind.util.Logging;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;



public class Globe extends ApplicationTemplate
{
    private final WorldWindow wwd;
    
    private boolean armed = false;// when the new button is clicked the plotting is 'armed'
    
    private ArrayList<Position> positions = new ArrayList<Position>();
    private boolean active = false;

    private final RenderableLayer layer;
    private final Polyline line;
    //private boolean active = false;
    
    private int numPoints = 1;// the current point number p1,p2,p3
    
    private ArrayList<RenderableLayer> pointsLayers = new ArrayList<RenderableLayer>();
    
    // results of the 3pp solver to return to the gui
    private double strike, dip, dipaz;
    private String quad;
    
    // arraylist to hold the placemarks for exporting
    private static ArrayList<PointPlacemark> placemarks = new ArrayList<PointPlacemark>(); 
    
    // arraylist to hold the models for exporting
    private ArrayList<ColladaRoot> models = new ArrayList<ColladaRoot>();

    
    private Position point2Pos;
    
    private boolean scopeIsThirdPoint = false;
    
    private String prefix = "P";
    
    private boolean hasPostfix = true;

    //private RenderableLayer pointsLayer;
   
    

    public Globe(final WorldWindow wwd, RenderableLayer lineLayer, Polyline polyline) 
    {
        this.wwd = wwd;
        //pointsLayer = new RenderableLayer();

        
        if (polyline != null)
        {
            line = polyline;
        }
        else
        {
            this.line = new Polyline();
            this.line.setFollowTerrain(true);
        }
        this.layer = lineLayer != null ? lineLayer : new RenderableLayer();
        //this.layer.addRenderable(this.line);
        this.wwd.getModel().getLayers().add(this.layer);

        this.wwd.getInputHandler().addMouseListener(new MouseAdapter()
        {
            public void mousePressed(MouseEvent mouseEvent)
            {
                if (armed && mouseEvent.getButton() == MouseEvent.BUTTON1)
                {
                    if (armed && (mouseEvent.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0)
                    {
                        if (!mouseEvent.isControlDown())
                        {
                            active = true;
                            addPosition();
                        }
                    }
                    mouseEvent.consume();
                }
            }
            
            public void mouseReleased(MouseEvent mouseEvent)
            {
                if (armed && mouseEvent.getButton() == MouseEvent.BUTTON1)
                {
                    if (positions.size() == 1)
                        removePosition();
                    active = false;
                    mouseEvent.consume();
                }
            }

            public void mouseClicked(MouseEvent mouseEvent)
            {
                if (armed && mouseEvent.getButton() == MouseEvent.BUTTON1)
                {
                    if (mouseEvent.isControlDown())
                        removePosition();
                    mouseEvent.consume();
                }
            }

            
        });


/*
        this.wwd.addPositionListener(new PositionListener()
        {
            public void moved(PositionEvent event)
            {
                if (!active)
                    return;

                if (positions.size() == 1)
                    addPosition();
                else
                    replacePosition();
            }
        });*/
    }
    
    public void getAllPlacemarks(){
    	
    	
    }

    /**
     * Returns the layer holding the polyline being created.
     *
     * @return the layer containing the polyline.
     */
    public RenderableLayer getLayer()
    {
        return this.layer;
    }

    /**
     * Returns the layer currently used to display the polyline.
     *
     * @return the layer holding the polyline.
     */
    public Polyline getLine()
    {
        return this.line;
    }

    /**
     * Removes all positions from the polyline.
     */
/*    public void clear()
    {
        while (this.positions.size() > 0)
            this.removePosition();
    }*/

    /**
     * Identifies whether the line builder is armed.
     *
     * @return true if armed, false if not armed.
     */
    public boolean isArmed()
    {
        return this.armed;
    }

    /**
     * Arms and disarms the line builder. When armed, the line builder monitors user input and builds the polyline in
     * response to the actions mentioned in the overview above. When disarmed, the line builder ignores all user input.
     *
     * @param armed true to arm the line builder, false to disarm it.
     */
    public void setArmed(boolean armed)
    {
        this.armed = armed;
    }

    /**
     * Takes in the positions when you click on the map.
     * Determiens which point it is and what to do with it.
     * Does the haversine calculations using Calculations.java
     * 
     */
    
    public void setNumPoints(int num)
    {
    	numPoints = num;
    }
    
    public int getNumPoints(){
    	
    	return numPoints;
    }
    
    public void setPrefix(String pre){
    	prefix = pre;
    }
    
    private void addPosition()
    {
        Position curPos = this.wwd.getCurrentPosition();
        
        System.out.println(curPos);// put out the lat/long/alt for debugging purposes
        
        
        
        
       
        
        if (curPos == null)
            return;

        this.positions.add(curPos);
        this.line.setPositions(this.positions);
        this.wwd.redraw();
        
       
        
       plotPoint(curPos);
       
       if(hasPostfix)
    	   numPoints++;
       
        
    }
    
    /**
     * 
     * @param curPos
     * @param isThirdPoint
     * @param point2
     * @param results
     * @param hldist
     * Plots a point on the globe.
     */
    private void plotPoint(Position curPos){
    	
    	
    	PointPlacemark pmStandard = new PointPlacemark(curPos);// the placemark
        
    	if(hasPostfix)
    		pmStandard.setLabelText(prefix + Integer.toString(numPoints));
    	else
    		pmStandard.setLabelText(prefix);

        pmStandard.setLineEnabled(false);
        
        // important must be clamped to ground
        pmStandard.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
        
        RenderableLayer pointsLayer = new RenderableLayer();
        
        pointsLayers.add(pointsLayer);
        
        placemarks.add(pmStandard);

        pointsLayer.addRenderable(pmStandard);
        insertBeforeCompass(this.wwd, pointsLayer);
        
        
    }

    private void replacePosition()
    {
        Position curPos = this.wwd.getCurrentPosition();
        if (curPos == null)
            return;

        int index = this.positions.size() - 1;
        if (index < 0)
            index = 0;

        //Position currentLastPosition = this.positions.get(index);
        this.positions.set(index, curPos);
        this.line.setPositions(this.positions);
        //this.firePropertyChange("LineBuilder.ReplacePosition", currentLastPosition, curPos);
        this.wwd.redraw();
    }

    private void removePosition()
    {
        if (this.positions.size() == 0)
            return;

        //Position currentLastPosition = this.positions.get(this.positions.size() - 1);
        this.positions.remove(this.positions.size() - 1);
        this.line.setPositions(this.positions);
       // this.firePropertyChange("LineBuilder.RemovePosition", currentLastPosition, null);
        this.wwd.redraw();
    }

    // ===================== Control Panel ======================= //
    // The following code is an example program illustrating LineBuilder usage. It is not required by the
    // LineBuilder class, itself.

    
    public void clearPoints(){
    	
    	for(RenderableLayer r : pointsLayers)
    	{
    		
    		r.removeAllRenderables();
        	r.dispose();
        	placemarks.clear();
        	models.clear();
    	}
    	
    	numPoints = 1;

        this.wwd.redraw();

    	
    }
    
    public void clearLastPoint(){
    	

    		
    		RenderableLayer r = pointsLayers.get(pointsLayers.size()-1);
	    	pointsLayers.remove(pointsLayers.size()-1);
	    	
	    	r.dispose();

	    	if(numPoints > 1)
	    		numPoints--;
	    	
	    	if(placemarks.size() > 1)
	    		placemarks.remove(placemarks.size()-1);
	    	

	    	
	    	this.wwd.redraw();
    
    	
    	
    }
    
    public Position getPoint2(){
    	return point2Pos;
    }
    
    
	public void exportKML(String path, ArrayList<String> names) {
		try{
	    	
		    // Create a StringWriter to collect KML in a string buffer
           // Writer stringWriter = new StringWriter();

            // Create a document builder that will write KML to the StringWriter
           // KMLDocumentBuilder kmlBuilder = new KMLDocumentBuilder(stringWriter);

			//for(String name : names)
        	//{
        	///	(
        	//}
		/*	for(String name : names){
				RenderableLayer rl = (RenderableLayer)(wwd.getModel().getLayers().getLayerByName(name));
				for(Renderable r : rl.getRenderables()){
					if(r instanceof PointPlacemark)
					{
						PointPlacemark p = (PointPlacemark)r;
						System.out.println(p.getLabelText());
					}
					
				}
			}*/
			for(String name : names)
			{
				for(Object o : this.wwd.getModel().getLayers().getLayerByName(name).getValues()){
		    			System.out.println(name + o.toString());
		    	}
			}
			
			
            String xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
								"<kml xmlns=\"http://www.opengis.net/kml/2.2\">" +
								"<Document>" +
									"<name>Placemarks</name>" +
									"<Folder>" +
										"<name>Annotations</name>";

         // Export the placemarks
            for(PointPlacemark p : placemarks){
            	
            	String placemarkString = "<Placemark>";
            	
            	placemarkString += "<name>" + p.getLabelText() + "</name>";
            	placemarkString += "<Point>" + 
            		"<coordinates>" + p.getPosition().getLongitude().getDegrees() + "," + p.getPosition().getLatitude().getDegrees() + "," + 0 + "</coordinates>";
    			placemarkString += "</Point>";
    			placemarkString += "</Placemark>";
    			
    			xmlString += placemarkString;
                        
            }
            
      
            
            
            xmlString += "</Folder>" +
				         "</Document>" +
				         "</kml>";
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

           // String results = "";
            
            StringWriter stringWriter = new StringWriter();
            transformer.transform(new StreamSource(new StringReader(xmlString)), new StreamResult(stringWriter));
            
            
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), "utf-8"))) {
            	writer.write(stringWriter.toString());
            }
            catch (Exception e){
            	System.err.println("Writing error.");
      }
            
		} catch (Exception e)
        {
            String message = Logging.getMessage("generic.ExceptionAttemptingToWriteXml", e.toString());
            Logging.logger().severe(message);
            e.printStackTrace();
        }
    }

    /**
     * Marked as deprecated to keep it out of the javadoc.
     *
     * @deprecated
     */
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        /**
		 * 
		 */
		private static final long serialVersionUID = 937100948174364269L;

		public AppFrame()
        {
            super(true, false, false);

           // Globe lineBuilder = new Globe(this.getWwd(), null, null);
            //this.getContentPane().add(new LinePanel(this.getWwd(), lineBuilder), BorderLayout.WEST);
            
        }
    }

    /**
     * Marked as deprecated to keep it out of the javadoc.
     *
     * @param args the arguments passed to the program.
     * @deprecated
     */
    public static void main(String[] args)
    {
        //noinspection deprecation
        ApplicationTemplate.start("World Wind Line Builder", Globe.AppFrame.class);
    }

	public double getStrike() {
		return strike;
	}

	public void setStrike(double strike) {
		this.strike = strike;
	}

	public double getDip() {
		return dip;
	}

	public void setDip(double dip) {
		this.dip = dip;
	}

	public double getDipaz() {
		return dipaz;
	}

	public void setDipaz(double dipaz) {
		this.dipaz = dipaz;
	}

	public String getQuad() {
		return quad;
	}

	public void setQuad(String quad) {
		this.quad = quad;
	}
	
	
	
	public static void addToPlacemarkList(PointPlacemark p){
		placemarks.add(p);
	}
	
	public boolean getIsThirdPoint(){
		return scopeIsThirdPoint;
	}

	public boolean isHasPostfix() {
		return hasPostfix;
	}

	public void setHasPostfix(boolean hasPostfix) {
		this.hasPostfix = hasPostfix;
	}
}
