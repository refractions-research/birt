/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.engine.emitter.pdf;

import java.awt.Color;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.report.engine.api.ITOCTree;
import org.eclipse.birt.report.engine.api.TOCNode;
import org.eclipse.birt.report.engine.api.script.IReportContext;
import org.eclipse.birt.report.engine.content.IReportContent;
import org.eclipse.birt.report.engine.i18n.EngineResourceHandle;
import org.eclipse.birt.report.engine.i18n.MessageConstants;
import org.eclipse.birt.report.engine.internal.util.BundleVersionUtil;
import org.eclipse.birt.report.engine.ir.Expression;
import org.eclipse.birt.report.engine.layout.emitter.IPage;
import org.eclipse.birt.report.engine.layout.emitter.IPageDevice;

import com.ibm.icu.util.ULocale;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.kernel.pdf.CompressionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfOutline;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Canvas;


public class PDFPageDevice implements IPageDevice
{

	/**
	 * The pdf Document object created by iText
	 */
	protected PdfDocument doc = null;

	/**
	 * The Pdf Writer
	 */
	protected PdfWriter writer = null;

	protected IReportContext context;

	protected IReportContent report;

	protected static Logger logger = Logger.getLogger( PDFPageDevice.class
			.getName( ) );

	protected PDFPage currentPage = null;

	protected HashMap<Float, Canvas> templateMap = new HashMap<Float, Canvas>( );

	protected HashMap<String, ImageData> imageCache = new HashMap<>( );

	/**
	 * the iText and Birt engine version info.
	 */
	protected static String[] versionInfo = new String[]{
			BundleVersionUtil
					.getBundleVersion( "org.eclipse.birt.report.engine" )};

	protected final static int MAX_PAGE_WIDTH = 14400000; // 200 inch
	protected final static int MAX_PAGE_HEIGHT = 14400000; // 200 inch
	
	//Property names for list of files to append or prepend to PDF output
	private static String APPEND_PROPERTY_NAME = "AppendList";
	private static String PREPEND_PROPERTY_NAME = "PrependList";

	public PDFPageDevice( OutputStream output, String title, String author,
			String subject, String description, IReportContext context,
			IReportContent report )
	{
		this.context = context;
		this.report = report;
		
		
		try
		{
			writer = new PdfWriter(new BufferedOutputStream(output));
			doc = new PdfDocument( writer );
			
			writer.setCompressionLevel(CompressionConstants.BEST_COMPRESSION );
//			writer.setRgbTransparencyBlending( true );
			EngineResourceHandle handle = new EngineResourceHandle(
					ULocale.forLocale( context.getLocale( ) ) );

			String creator = handle.getMessage( MessageConstants.PDF_CREATOR,
					versionInfo );
			doc.getDocumentInfo().setCreator(creator);
			
			if ( null != author ) 
			{
				doc.getDocumentInfo().setAuthor( author );
			}
			
			if ( null != title ) 
			{
				doc.getDocumentInfo().setTitle( title );
			}
			
			if ( null != subject )
			{
				doc.getDocumentInfo().setSubject( subject );
				doc.getDocumentInfo().setKeywords( subject );
			}
			if ( description != null )
			{
				doc.getDocumentInfo().setMoreInfo( "Description", description );
			}
			
			//Add in prepending PDF's
			//modified here. This will grab a global variable called 
			//appendPDF, and take a list of strings of PDF files to 
			//append to the end.
			//this is where we will test the merge
			List<InputStream> pdfs = new ArrayList<InputStream>();
			
			//removed using the runtime instance of the report and switched to using the designtime
			//instance per request.
			//String list = (String) context.getReportRunnable().getProperty("AppendList");
			//String list = (String) context.getDesignHandle().getProperty("AppendList");
			Map<String, Expression> props = report.getDesign().getUserProperties();
			
			//added null check
			if (props != null)
			{
				Object listObject = props.get(PDFPageDevice.PREPEND_PROPERTY_NAME);
				
				if (listObject != null)
				{
					Expression exp = (Expression) listObject;
					
					Object result = context.evaluate(exp);
					//there are two options here. 1 is the user property "AppendList" is a comma-seperated
					//string list. If so, check that it is a String, and split it.
					if (result instanceof String)
					{
						String list = (String) result;
					
						//check that the report variable AppendList is set, and actually has value
						if (list != null)
						{
							if (list.length() > 0)
							{
								//iterate over the list, and create a fileinputstream for each file location. 
								for (String s : list.split(","))
								{
									//If there is an exception creating the input stream, don't stop execution.
									//Just graceffully let the user know that there was an error with the variable.
									try {
										String fileName = s.trim();
										
										File f = new File(fileName);
										
										if (f.exists())
										{
											FileInputStream fis = new FileInputStream( f );
											
											pdfs.add(fis);
										}
									} catch (Exception e) {
										logger.log( Level.WARNING, e.getMessage( ), e );
									}
								}
							}
						}
					}
					
					//The other is a "Named Expression", which is basically a user property that is the result
					//of an expression instead of a string literal. This should be set as an arraylist through
					//BIRT script
					if (result instanceof ArrayList)
					{
						ArrayList<String> pdfList = (ArrayList<String>) result;
						
						for (String fileName : pdfList)
						{
							//If there is an exception creating the input stream, don't stop execution.
							//Just graceffully let the user know that there was an error with the variable.
							try {
								File f = new File(fileName);
								
								if (f.exists())
								{
									FileInputStream fis = new FileInputStream( f );
									
									pdfs.add(fis);
								}
							} catch (Exception e) {
								logger.log( Level.WARNING, e.getMessage( ), e );
							}
						}
					}
					
					//check size of PDFs to make sure we aren't calling this on a 0 size array
					if (pdfs.size() > 0)
					{
						//this hasn't been initialized yet, open the doc
//						if ( !this.doc.isOpen( ) )
//							this.doc.open( );
						concatPDFs(pdfs, true);
					}
				}
			}
			//End Modification
		}
		catch (BirtException be) {
			logger.log( Level.SEVERE, be.getMessage( ), be );
		}
	}

	/**
	 * constructor for test
	 *
	 * @param output
	 */
	public PDFPageDevice( OutputStream output )
	{
		writer = new PdfWriter(new BufferedOutputStream(output));
		doc = new PdfDocument( writer );
	}

	public void setPDFTemplate( Float scale, Canvas totalPageTemplate )
	{
		templateMap.put( scale, totalPageTemplate );
	}

	public HashMap<Float, Canvas> getTemplateMap( )
	{
		return templateMap;
	}

	public Canvas getPDFTemplate( Float scale )
	{
		return templateMap.get( scale );
	}

	public boolean hasTemplate( Float scale )
	{
		return templateMap.containsKey( scale );
	}

	public HashMap<String, ImageData> getImageCache( )
	{
		return imageCache;
	}

	public void close( ) throws Exception
	{
		
		//modified here. This will grab a global variable called 
		//appendPDF, and take a list of strings of PDF files to 
		//append to the end.
		//this is where we will test the merge
		List<InputStream> pdfs = new ArrayList<InputStream>();
		
		//removed using the runtime instance of the report and switched to using the designtime
		//instance per request.
		//String list = (String) context.getReportRunnable().getProperty("AppendList");
		//String list = (String) context.getDesignHandle().getProperty("AppendList");
		Map<String, Expression> props = report.getDesign().getUserProperties();
		
		//added null check
		if (props != null)
		{
			Object listObject = props.get(PDFPageDevice.APPEND_PROPERTY_NAME);
			
			if (listObject != null)
			{
				Expression exp = (Expression) listObject;
				
				Object result = context.evaluate(exp);
				//there are two options here. 1 is the user property "AppendList" is a comma-seperated
				//string list. If so, check that it is a String, and split it.
				if (result instanceof String)
				{
					String list = (String) result;
				
					//check that the report variable AppendList is set, and actually has value
					if (list != null)
					{
						if (list.length() > 0)
						{
							//iterate over the list, and create a fileinputstream for each file location. 
							for (String s : list.split(","))
							{
								//If there is an exception creating the input stream, don't stop execution.
								//Just graceffully let the user know that there was an error with the variable.
								try {
									String fileName = s.trim();
									
									File f = new File(fileName);
									
									if (f.exists())
									{
										FileInputStream fis = new FileInputStream( f );
										
										pdfs.add(fis);
									}
								} catch (Exception e) {
									logger.log( Level.WARNING, e.getMessage( ), e );
								}
							}
						}
					}
				}
				
				//The other is a "Named Expression", which is basically a user property that is the result
				//of an expression instead of a string literal. This should be set as an arraylist through
				//BIRT script
				if (result instanceof ArrayList)
				{
					ArrayList<String> pdfList = (ArrayList<String>) result;
					
					for (String fileName : pdfList)
					{
						//If there is an exception creating the input stream, don't stop execution.
						//Just graceffully let the user know that there was an error with the variable.
						try {
							File f = new File(fileName);
							
							if (f.exists())
							{
								FileInputStream fis = new FileInputStream( f );
								
								pdfs.add(fis);
							}
						} catch (Exception e) {
							logger.log( Level.WARNING, e.getMessage( ), e );
						}
					}
				}
				
				//check size of PDFs to make sure we aren't calling this on a 0 size array
				if (pdfs.size() > 0)
				{
					concatPDFs(pdfs, true);
				}
			}
		}
		//End Modification
			    
//		writer.setPageEmpty( false );
		doc.close();
		writer.flush();
		writer.close();
	}

	public IPage newPage( int width, int height, Color backgroundColor )
	{
		int w = Math.min( width, MAX_PAGE_WIDTH );
		int h = Math.min( height, MAX_PAGE_HEIGHT );
		currentPage = createPDFPage( w, h );
		currentPage.drawBackgroundColor( backgroundColor, 0, 0, w, h );
		return currentPage;
	}

	protected PDFPage createPDFPage( int pageWidth, int pageHeight )
	{
		return new PDFPage( pageWidth, pageHeight, this, doc );
	}

	public void createTOC( Set<String> bookmarks )
	{
		// we needn't create the TOC if there is no page in the PDF file.
		// the doc is opened only if the user invokes newPage.
//		if ( !doc.isOpen( ) )
//		{
//			return;
//		}
		if (doc.getNumberOfPages() == 0)
		{
			return;
		
		}
		
		if ( bookmarks.isEmpty( ) )
		{
			doc.getCatalog().put(PdfName.PageMode, PdfName.UseNone);
//			writer.setViewerPreferences( PdfWriter.PageModeUseNone );
			return;
		}
		ULocale ulocale = null;
		Locale locale = context.getLocale( );
		if ( locale == null )
		{
			ulocale = ULocale.getDefault( );
		}
		else
		{
			ulocale = ULocale.forLocale( locale );
		}
		// Before closing the document, we need to create TOC.
		ITOCTree tocTree = report.getTOCTree( "pdf", //$NON-NLS-1$
				ulocale );
		if ( tocTree == null )
		{
//			writer.setViewerPreferences( PdfWriter.PageModeUseNone );
			doc.getCatalog().put(PdfName.PageMode, PdfName.UseNone);
		}
		else
		{
			TOCNode rootNode = tocTree.getRoot( );
			if ( rootNode == null || rootNode.getChildren( ).isEmpty( ) )
			{
//				writer.setViewerPreferences( PdfWriter.PageModeUseNone );
				doc.getCatalog().put(PdfName.PageMode, PdfName.UseNone);
			}
			else
			{
				doc.getCatalog().put(PdfName.PageMode, PdfName.UseOutlines);
//				writer.setViewerPreferences( PdfWriter.PageModeUseOutlines );
				
				TOCHandler tocHandler = new TOCHandler( rootNode, doc.getOutlines(true), bookmarks );
				tocHandler.createTOC( );
			}
		}
	}

	protected TOCHandler createTOCHandler( TOCNode root, PdfOutline outline,
			Set<String> bookmarks )
	{
		return new TOCHandler( root, outline, bookmarks );
	}
	
	/**
	 * Patched PDF to Combine PDF Files
	 * 
	 * Given a list of PDF Files
	 * When a user wants to append PDf files to a PDF emitter output
	 * Then Append the PDF files to the output stream or output file
	 * 
	 * @param streamOfPDFFiles
	 * @param paginate
	 */
	 public void concatPDFs(List<InputStream> streamOfPDFFiles, boolean paginate) {
		    PdfDocument document = doc;
		    try {		      
		      for (InputStream is : streamOfPDFFiles) {
		    	  PdfDocument pdfReader = new PdfDocument(new PdfReader(is));
		    	  for (int i = 0; i < pdfReader.getNumberOfPages(); i ++) {
		    		  PdfPage currentPage = pdfReader.getPage(i);
		    		  currentPage.copyTo(document);
		    	  }
		    	  pdfReader.close();
		      }
		      
		    } catch (Exception e) {
		      e.printStackTrace();  
		    }
		  }	
}