/*******************************************************************************
 * Copyright (c)2008 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.engine.util;

import java.awt.image.BufferedImage;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.logging.Logger;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.image.resources.Messages;

public class SvgFile
{

	private static Logger logger = Logger.getLogger( SvgFile.class.getName( ) );

	static boolean isSvg = false;

	public static boolean isSvg( String uri )
	{
		if ( uri != null && uri.endsWith( ".svg" ) )
		{
			isSvg = true;
		}
		else
		{
			isSvg = false;
		}
		return isSvg;
	}
	
	public static boolean isSvg( String mimeType, String uri, String extension )
	{
		isSvg = ( ( mimeType != null ) && mimeType
				.equalsIgnoreCase( "image/svg+xml" ) ) //$NON-NLS-1$
				|| ( ( uri != null ) && uri.toLowerCase( ).endsWith( ".svg" ) ) //$NON-NLS-1$
				|| ( ( extension != null ) && extension.toLowerCase( )
						.endsWith( ".svg" ) ); //$NON-NLS-1$
		return isSvg;
	}

	public static byte[] transSvgToArray( String uri ) throws Exception
	{
		InputStream in = new URL( uri ).openStream( );
		try
		{
			return transSvgToArray( in );
		}
		finally
		{
			in.close( );
		}
	}

	public static byte[] transSvgToArray( InputStream inputStream )
			throws Exception
	{
		PNGTranscoder transcoder = new PNGTranscoder( ) {
			//hack to get around class loading issue
			//https://bugs.eclipse.org/bugs/show_bug.cgi?id=506589
			//TODO: I am sure there must be a a better way to do this
			 private WriteAdapter getWriteAdapter(String className) {
			        WriteAdapter adapter;
			        try {
			            Class<?> clazz = Class.forName(className);
			            adapter = (WriteAdapter)clazz.getDeclaredConstructor().newInstance();
			            return adapter;
			        } catch (ClassNotFoundException e) {
			            return null;
			        } catch (InstantiationException e) {
			            return null;
			        } catch (IllegalAccessException e) {
			            return null;
			        } catch (NoSuchMethodException e) {
			            return null;
			        } catch (InvocationTargetException e) {
			            return null;
			        }
			    }
			 
			public void writeImage(BufferedImage img, TranscoderOutput output)
		            throws TranscoderException {

		        OutputStream ostream = output.getOutputStream();
		        if (ostream == null) {
		            throw new TranscoderException(
		                Messages.formatMessage("png.badoutput", null));
		        }

		        //
		        // This is a trick so that viewers which do not support the alpha
		        // channel will see a white background (and not a black one).
		        //
		        boolean forceTransparentWhite = false;

		        if (hints.containsKey(PNGTranscoder.KEY_FORCE_TRANSPARENT_WHITE)) {
		            forceTransparentWhite =
		                    (Boolean) hints.get
		                            (PNGTranscoder.KEY_FORCE_TRANSPARENT_WHITE);
		        }

		        if (forceTransparentWhite) {
		            SinglePixelPackedSampleModel sppsm;
		            sppsm = (SinglePixelPackedSampleModel)img.getSampleModel();
		            forceTransparentWhite(img, sppsm);
		        }

		        WriteAdapter adapter = getWriteAdapter(
		                "org.apache.batik.ext.awt.image.codec.png.PNGTranscoderInternalCodecWriteAdapter");
		        if (adapter == null) {
		            adapter = getWriteAdapter(
		                "org.apache.batik.transcoder.image.PNGTranscoderImageIOWriteAdapter");
		        }
		        if (adapter == null) {
		            throw new TranscoderException(
		                    "Could not write PNG file because no WriteAdapter is availble");
		        }
		        adapter.writeImage(this, img, output);
		    }
		};
		// create the transcoder input
		TranscoderInput input = new TranscoderInput( inputStream );
		// create the transcoder output
		ByteArrayOutputStream ostream = new ByteArrayOutputStream( );
		TranscoderOutput output = new TranscoderOutput( ostream );
		transcoder.transcode( input, output );
		// flush the stream
		ostream.flush( );
		// use the output stream as Image input stream.
		return ostream.toByteArray( );
	}
}
