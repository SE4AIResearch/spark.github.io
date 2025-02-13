/**
 * VideoTransmit.java
 *
 * This is to transmit Video in JPG format and allow the choice of 
 * JPG quality to transmit over the internet.
 * Most of these codes are inspired by Project "Digital video processing 
 * for surveillance in wireless LAN systems" University of Missouri-Columbia                                  
 * by Sitaram Sukumar and Ravishankar Manishankar
 * Created on den 20 september 2004
 */

import java.awt.*;
import javax.media.*;
import javax.media.protocol.*;
import javax.media.format.*;
import javax.media.control.TrackControl;
import javax.media.control.QualityControl;
import javax.media.control.FormatControl;
import java.io.*;

/** The VideoTransmit class, creates objects for transmitting video.
 * @author Ng Sae Kiat <saengx-4@student.luth.se>
 * @version 1.0
 */
public class VideoTransmit {

    //Input MediaLocator, Can be a file or http or capture source
    private MediaLocator locator;
    private String ipAddress;
    private String port;
    private float quality;

    private Processor processor = null;
    private DataSink  rtptransmitter = null;
    private DataSource dataOutput = null;
    private DataSource dataInput = null;
    private Processor inProc = null;

    /**
     * Creates a new VideoTransmit Object from a given MediaLocator.
     *
     * @param locator the MediaLocator to transmit from.
     * @param ipAddress the address of the receiver.
     * @param port the remote port to send to.
     */    
    public VideoTransmit(MediaLocator locator,
			 String ipAddress,
			 String port) {
	this.locator = locator;
	this.ipAddress = ipAddress;
	this.port = port;
    }
     
    /**
     * Creates a new VideoTransmit Object from a given Processor.
     *
     * @param processor the Processor to transmit from.
     * @param ipAddress the address of the receiver.
     * @param port the remote port to send to.
     * @param quality quality of the JPEG encoding.
     */    
    public VideoTransmit(Processor processor, String ipAddress, String port, float quality) {
         System.err.println("VideoTransmit: Creating new VideoTransmit from Processor");
         this.locator = null;
         this.ipAddress = ipAddress;
         this.port = port;
         this.inProc = processor;
         this.quality = quality;
         //this.dataInput = source;
     }

    /** Starts the transmission. Returns null if transmission started ok.
     * Otherwise returns a string with the reason why the setup failed.
     * @return null if successful.
     */
    public synchronized String start() {
	String result;
        System.err.println("VideoTransmit: start() called, creating processor");
	// Create a processor for the specified media locator
	// and program it to output JPEG/RTP
	result = createProcessor();
	if (result != null)
	    return result;

        System.err.println("VideoTransmit: Creating transmitter");
	// Create an RTP session to transmit the output of the
	// processor to the specified IP address and port no.
	result = createTransmitter();
	if (result != null) {
	    processor.close();
	    processor = null;
	    return result;
	}

	// Start the transmission
	processor.start();
	return null;
    }

    /**
     * Stops the transmission if already started
     */
    public void stop() {
	synchronized (this) {
	    if (processor != null) {
		processor.stop();
		processor.close();
	
	processor = null;
	
	rtptransmitter.close();
		rtptransmitter = null;
	    }
	}
    }

    private String createProcessor() {
        DataSource ds = null;
        DataSource clone;
        System.err.println("VideoTransmit: createProcessor() called");
	if (locator == null && inProc == null) 
	    return "Input is null";
        else if (inProc != null) {
            System.err.println("Using given processor");
            processor = inProc;
        }
        else {
            try {
                ds = Manager.createDataSource(locator);
            } catch (Exception e) {
                return "Couldn't create DataSource";
            }
        }

	// Try to create a processor to handle the input media locator
        if (processor == null) {
            try {
                processor = Manager.createProcessor(ds);
            } catch (NoProcessorException npe) {
                return "Couldn't create processor";
            } catch (IOException ioe) {
                return "IOException creating processor";
            } 
        }
        System.err.println("Waiting for processor to become configured");
        // Wait for it to configure
	boolean result = waitForState(processor, Processor.Configured);
	if (result == false)
	    return "Couldn't configure processor";

	// Get the tracks from the processor
	TrackControl [] tracks = processor.getTrackControls();
	
	if (tracks == null || tracks.length < 1)
	    return "Couldn't find tracks in processor";
	boolean programmed = false; 


        // Search through the tracks for a video track
	for (int i = 0; i < tracks.length; i++) {
	    Format format = tracks[i].getFormat();
	    if (  tracks[i].isEnabled() && format instanceof VideoFormat && !programmed) {
		
		// Found a video track. Try to program it to output JPEG/RTP
		// Make sure the sizes are multiple of 8's.
		Dimension size = ((VideoFormat)format).getSize();
                
                //VideoFormat.format.setFrameRate(float(30.0));
                
                float frameRate = ((VideoFormat)format).getFrameRate();
                
                System.err.println(" Test FRAME " + frameRate);
               //frameRate = ((float)30.0);
                
		int w = (size.width % 8 == 0 ? size.width :
				(int)(size.width / 8) * 8);
		int h = (size.height % 8 == 0 ? size.height :
				(int)(size.height / 8) * 8);
		VideoFormat jpegFormat = new VideoFormat(VideoFormat.JPEG_RTP,
							 new Dimension(w, h),
							 Format.NOT_SPECIFIED,
							 Format.byteArray,
							 frameRate);
               
               // tracks[i].setFrameRate(float(30.0));
		System.err.println("Just before Setformat");
                tracks[i].setFormat(jpegFormat);
		System.err.println("Video transmitted as:");
		System.err.println("  " + jpegFormat);
		// Assume succesful
		programmed = true;

	    } else
		tracks[i].setEnabled(false);
	}

	if (!programmed)
	    return "Couldn't find video track";

	// Set the output content descriptor to RAW_RTP
	ContentDescriptor cd = new ContentDescriptor(ContentDescriptor.RAW_RTP);
	processor.setContentDescriptor(cd);

	// Realize the processor. This will internally create a flow
	// graph and attempt to create an output datasource for JPEG/RTP
	// video frames.
	result = waitForState(processor, Controller.Realized);
	if (result == false)
	    return "Couldn't realize processor";

        System.err.println("Quality Transmited in % of the original quality = "+quality);
	setJPEGQuality(processor, quality/100);

	// Get the output data source of the processor
	dataOutput = processor.getDataOutput();
	return null;
    }
    
/*
    //Change Format
    private void changeFormat( DataSource ds, Format format ){
//---------------------------------------------------------------------------------------        

        System.err.println("Entering Chnage Format");
        formatControls = ((CaptureDevice) ds).getFormatControls();

        if ( formatControls == null  ||  formatControls.length == 0 )
                return ;
        for ( int i = 0; i < formatControls.length; i++ )
        {
                if ( formatControls[i] == null )
                        continue;
                formats = formatControls[i].getSupportedFormats();
                for ( int j = 0;  j < formats.length;  j++ )
                {
                       // if ( formats[j].matches(format) )
                        {
                            System.err.println("Format CHANGED");
                                formatControls[i].setFormat(format);
                                break;
                        }
                }
                return ;
        }
        return ;        
}
*/
    
    
    // Creates an RTP transmit data sink. This is the easiest way to create
    // an RTP transmitter. The other way is to use the RTPSessionManager API.
    // Using an RTP session manager gives you more control if you wish to
    // fine tune your transmission and set other parameters.
    private String createTransmitter() {
	// Create a media locator for the RTP data sink.
	// For example:
        //    rtp://128.206.23.202.24680/video
	System.err.println("VideoTransmit: createTransmitter() called");
        String rtpURL = "rtp://" + ipAddress + ":" + port + "/video";
	MediaLocator outputLocator = new MediaLocator(rtpURL);

	// Create a data sink, open it and start transmission. It will wait
	// for the processor to start sending data. So we need to start the
	// output data source of the processor. We also need to start the
	// processor itself, which is done after this method returns.
	try {
	    rtptransmitter = Manager.createDataSink(dataOutput, outputLocator);
	    rtptransmitter.open();
	    rtptransmitter.start();
	    dataOutput.start();
	} catch (MediaException me) {
	    return "Couldn't create RTP data sink";
	} catch (IOException ioe) {
	    return "Couldn't create RTP data sink";
	}
	
	return null;
    }

    
   
    /**
     * Setting the encoding quality to the specified value on the JPEG encoder.
     * 0.5 is a good default.
     *
     * @param p the Player to modify
     * @param val the quality
     */
    void setJPEGQuality(Player p, float val) {

	Control cs[] = p.getControls();
	QualityControl qc = null;
	VideoFormat jpegFmt = new VideoFormat(VideoFormat.JPEG);
        
	// Loop through the controls to find the Quality control for
 	// the JPEG encoder.
	for (int i = 0; i < cs.length; i++) {
	
        if (cs[i] instanceof QualityControl &&
		cs[i] instanceof Owned) {
		Object owner = ((Owned)cs[i]).getOwner();
		// Check to see if the owner is a Codec.
		// Then check for the output format.
		if (owner instanceof Codec) {
		    Format fmts[] = ((Codec)owner).getSupportedOutputFormats(null);
		    for (int j = 0; j < fmts.length; j++) {
			if (fmts[j].matches(jpegFmt)) {
			    qc = (QualityControl)cs[i];
	    		    qc.setQuality(val);
                            
			    System.err.println("- Setting quality to " + 
					val + " on " + qc);
			    break;
			}
		    }
		}
		if (qc != null)
		    break;
	    }
	}
    }

    /****************************************************************
     * Convenience methods to handle processor's state changes.
     ****************************************************************/
    private Integer stateLock = new Integer(0);
    private boolean failed = false;
    Integer getStateLock() {
	return stateLock;
    }

    void setFailed() {
	failed = true;
   
 }
    
    private synchronized boolean waitForState(Processor p, int state) {
	p.addControllerListener(new StateListener());
	failed = false;

	// Call the required method on the processor
	if (state == Processor.Configured) {
	    p.configure();
	} else if (state == Processor.Realized) {
	    p.realize();
	}
	
	// Wait until we get an event that confirms the
	// success of the method, or a failure event.
	// See StateListener inner class
	while (p.getState() < state && !failed) {
	    synchronized (getStateLock()) {
		try {
		    getStateLock().wait();
		} catch (InterruptedException ie) {
		    return false;
		}
	    }
	}

	if (failed)
	    return false;
	else
	    return true;
    }





    /****************************************************************
     * Inner Classes
     ****************************************************************/

    class StateListener implements ControllerListener {

	public void controllerUpdate(ControllerEvent ce) {

	    // If there was an error during configure or
	    // realize, the processor will be closed
	    if (ce instanceof ControllerClosedEvent)
		setFailed();

	    // All controller events, send a notification
	    // to the waiting thread in waitForState method.
	    if (ce instanceof ControllerEvent) {
		synchronized (getStateLock()) {
		    getStateLock().notifyAll();
		}
	    }
	}
    }


    /****************************************************************
     VideoTransmit class
     ****************************************************************/
/*    
    public static void main(String [] args) {
	// We need three parameters to do the transmission
	// For example,
	// java VideoTransmit file:/C:/media/test.mov  129.130.131.132 42050
	


	if (args.length < 3) {
	    System.err.println("Usage: VideoTransmit <sourceURL> <destIP> <destPort>");
	    System.exit(-1);
	}
	
	// Create a video transmit object with the specified params.
	VideoTransmit vt = new VideoTransmit(new MediaLocator(args[0]),
					     args[1],
					     args[2]);
	// Start the transmission
	String result = vt.start();

	// result will be non-null if there was an error. The return
	// value is a String describing the possible error. Print it.
	if (result != null) {
	    System.err.println("Error : " + result);
	    System.exit(0);
	}

	System.err.println("Start transmission...");
        System.err.println("Press Button 'STOP' to stop");
	
	// Transmit for 60 seconds and then close the processor
	// This is a safeguard when using a capture data source
	// so that the capture device will be properly released
	// before quitting.
	// The right thing to do would be to have a GUI with a
	// "Stop" button that would call stop on VideoTransmit
	
//        try {
//            getinput()
//            Thread.currentThread().sleep(100000);
//	} catch (InterruptedException ie) {
//	}

	// Stop the transmission
	vt.stop();
	System.err.println("...transmission ended.");
	System.exit(0);
        
        
    }
*/
}



