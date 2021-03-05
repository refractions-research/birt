/*******************************************************************************
 * Copyright (c) 2008 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.engine.layout.pdf.font;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Locale;

import com.itextpdf.io.font.constants.FontStyles;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;

public class FontCacheUtils
{

	static public void main( String[] args ) throws Exception
	{
		createUnicodeText( "unicode.txt" );
//		createUnicodePDF( "pdf", Locale.CHINESE, "unicode.pdf" );
	}

	// FontFactory.registerDirectory( "e:\\windows\\fonts\\" );
	// createCache( "Courier New", BaseFont.IDENTITY_H );

	static void createUnicodeText( String fileName ) throws IOException
	{
		OutputStream out = new FileOutputStream( fileName );
		Writer writer = new OutputStreamWriter( out, "utf-8" );
		for ( int seg = 0; seg < 0xFF; seg++ )
		{
			StringBuffer sb = new StringBuffer( );
			sb.append( Integer.toHexString( seg * 0xFF ) ).append( '\n' );
			writer.write( sb.toString( ) );
			for ( int hi = 0; hi < 16; hi++ )
			{
				sb.setLength( 0 );
				for ( int lo = 0; lo < 16; lo++ )
				{
					char ch = (char) ( seg * 0xFF + hi * 16 + lo );
					if ( Character.isISOControl( ch ) )
					{
						ch = '?';
					}
					sb.append( ch ).append( ' ' );
				}
				sb.append( '\n' );
				writer.write( sb.toString( ) );
			}
		}
		writer.close( );
	}

//	static void createUnicodePDF( String format, Locale locale, String fileName )
//			throws Exception
//	{
//		FontMappingManager manager = FontMappingManagerFactory.getInstance( )
//				.getFontMappingManager( format, locale );
//
//		// step 1: creation of a document-object
//		PdfDocument document = new PdfDocument( );
//		
//		PdfWriter writer = PdfWriter.getInstance( document,
//				new FileOutputStream( fileName ) );
//		document.open( );
//		for ( int seg = 0; seg < 0xFF; seg++ )
//		{
//			PdfContentByte cb = writer.getDirectContent( );
//			cb.beginText( );
//			for ( int hi = 0; hi < 16; hi++ )
//			{
//				for ( int lo = 0; lo < 16; lo++ )
//				{
//					int x = 100 + hi * 32;
//					int y = 100 + lo * 32;
//					char ch = (char) ( seg * 0xFF + hi * 16 + lo );
//
//					String fontFamily = manager.getDefaultPhysicalFont( ch );
//					PdfFont bf = manager.createFont( fontFamily, FontStyles.NORMAL );
//					cb.setFontAndSize( bf, 16 );
//					cb.setTextMatrix( x, y );
//					cb.showText( new String( new char[]{ch} ) );
//				}
//			}
//			cb.endText( );
//		}
//		document.close( );
//	}

	static void createFontIndex( String fontName, String encoding, Writer writer )
			throws Exception
	{
		PdfFont font = PdfFontFactory.createFont( fontName, encoding, false );
		ArrayList charSegs = new ArrayList( );
		int start = 0;
		int end = 0;
		for ( char ch = 0; ch < 0xFFFF; ch++ )
		{
			if ( font.containsGlyph( ch ) )
			{
				if ( start == -1 )
				{
					start = ch;
				}
				end = ch;
			}
			else
			{
				if ( start != -1 )
				{
					charSegs.add( new CharSegment( start, end, fontName ) );
					start = -1;
				}
			}
		}
		if ( start != -1 )
		{
			charSegs.add( new CharSegment( start, end, fontName ) );
		}
		for ( int i = 0; i < charSegs.size( ); i++ )
		{
			StringBuffer sb = new StringBuffer( );
			sb.append( "<block region-start=\"" ).append( start ).append(
					"\" region-end=\"" ).append( end ).append( "\"/>" ).append(
					'\n' );
			writer.write( sb.toString( ) );
		}
	}
}
