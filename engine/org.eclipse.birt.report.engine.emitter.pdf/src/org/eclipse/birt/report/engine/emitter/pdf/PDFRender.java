/*******************************************************************************
 * Copyright (c) 2004,2007 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.engine.emitter.pdf;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.IHTMLActionHandler;
import org.eclipse.birt.report.engine.api.RenderOption;
import org.eclipse.birt.report.engine.api.impl.Action;
import org.eclipse.birt.report.engine.api.script.IReportContext;
import org.eclipse.birt.report.engine.content.IHyperlinkAction;
import org.eclipse.birt.report.engine.content.IReportContent;
import org.eclipse.birt.report.engine.emitter.EmitterUtil;
import org.eclipse.birt.report.engine.emitter.IEmitterServices;
import org.eclipse.birt.report.engine.layout.emitter.IPageDevice;
import org.eclipse.birt.report.engine.layout.emitter.PageDeviceRender;
import org.eclipse.birt.report.engine.nLayout.area.IArea;
import org.eclipse.birt.report.engine.nLayout.area.IContainerArea;
import org.eclipse.birt.report.engine.nLayout.area.IImageArea;
import org.eclipse.birt.report.engine.nLayout.area.ITemplateArea;
import org.eclipse.birt.report.engine.nLayout.area.ITextArea;
import org.eclipse.birt.report.engine.nLayout.area.style.TextStyle;
import org.eclipse.birt.report.model.api.ReportDesignHandle;

import com.itextpdf.kernel.pdf.navigation.PdfDestination;

public class PDFRender extends PageDeviceRender
{

	/**
	 * The output stream
	 */
	protected OutputStream output = null;

	protected PDFPage currentPage = null;

	protected PDFPageDevice currentPageDevice = null;

	protected HashMap<String, PdfDestination> bookmarks = new HashMap<>( );

	//cache of all pages created
	protected Set<PDFPage> allPages = new HashSet<>();
	//set of hyperlinks to create after document is create; we
	//need to make sure bookmarks are created before we create the hyperlinks
	private Set<Object[]> hyperlinks = new HashSet<>();
	
	public PDFRender( IEmitterServices services ) throws EngineException
	{
		initialize( services );
	}

	public IPageDevice createPageDevice( String title, String author,
			String subject, String description, IReportContext context,
			IReportContent report ) throws Exception
	{
		currentPageDevice = new PDFPageDevice( output, title, author, subject,
				description, context, report );
		return currentPageDevice;
	}

	public String getOutputFormat( )
	{
		return "pdf";
	}

	protected void newPage( IContainerArea page )
	{
		super.newPage( page );
		currentPage = (PDFPage) pageGraphic;
		allPages.add(currentPage);
	}

	public void visitImage( IImageArea imageArea )
	{
		int imageX = currentX + getX( imageArea );
		int imageY = currentY + getY( imageArea );
		super.visitImage( imageArea );
		createBookmark( imageArea, imageX, imageY );
		createHyperlink( imageArea, imageX, imageY );
	}

	public void visitText( ITextArea textArea )
	{
		super.visitText( textArea );
		int x = currentX + getX( textArea );
		int y = currentY + getY( textArea );
		createBookmark( textArea, x, y );
		createHyperlink( textArea, x, y );
	}

	public void visitAutoText( ITemplateArea templateArea )
	{
		super.visitAutoText( templateArea );
		int x = currentX +  getX( templateArea );
		int y = currentY + getY( templateArea );
		int width = getWidth(templateArea);
		int height = getHeight(templateArea);
		currentPage.createTotalPageTemplate( x, y, width, height );
	}

	public void setTotalPage( ITextArea totalPage )
	{
		super.setTotalPage( totalPage );
		allPages.forEach(p->p.drawTotalPage(totalPage.getText(), totalPage.getTextStyle()));
	}

	/**
	 * Closes the document.
	 *
	 * @param rc
	 *            the report content.
	 */
	public void end( IReportContent rc )
	{

		//add hyperlinks
		for (Object[] link : hyperlinks) {
			String bookmark = (String)link[2];
			if (bookmarks.get(bookmark) == null) continue;
			
			PDFPage page = (PDFPage) link[0];
			String tlink = (String)link[1];
			String targetWindow = (String)link[2];
			int type = (int)link[4];
			int x = (int)link[5];
			int y = (int)link[6];
			int width = (int)link[7];
			int height = (int)link[8];
			page.createHyperlink(tlink,bookmarks.get(bookmark), targetWindow, type, x,y, width,height);
		}
		
		createTOC( );
		super.end( rc );
	}

	protected void drawContainer( IContainerArea container )
	{
		super.drawContainer( container );
		int x = currentX + getX( container );
		int y = currentY + getY( container );
		createBookmark( container, x, y );
		createHyperlink( container, x, y );
	}

	/**
	 * Initializes the pdfEmitter.
	 *
	 * @param services
	 *            the emitter services object.
	 * @throws EngineException
	 */
	private void initialize( IEmitterServices services ) throws EngineException
	{
		this.services = services;
		// Gets the output file name from RenderOptionBase.OUTPUT_FILE_NAME.
		// It has the top preference.
		reportRunnable = services.getReportRunnable( );
		if ( reportRunnable != null )
		{
			reportDesign = (ReportDesignHandle) reportRunnable
					.getDesignHandle( );
		}

		this.context = services.getReportContext( );
		this.output = EmitterUtil.getOuputStream( services, "report.pdf" );
	}

	protected void drawTextAt( ITextArea text, int x, int y, int width,
			int height, TextStyle textInfo )
	{
		currentPage.drawText( text.getText( ), x, y, width, height, textInfo );
	}


	
	private void createHyperlink( IArea area, int x, int y )
	{
		IHyperlinkAction hlAction = area.getAction( );
		if ( null != hlAction )
			try
			{
				String systemId = reportRunnable == null
						? null
						: reportRunnable.getReportName( );
				int width = getWidth( area );
				int height = getHeight( area );
				String bookmark = hlAction.getBookmark( );
				String targetWindow = hlAction.getTargetWindow( );
				int type = hlAction.getType( );
				Action act = new Action( systemId, hlAction );
				String link = null;
				IHTMLActionHandler actionHandler = null;
				Object ac = services.getOption( RenderOption.ACTION_HANDLER );
				if ( ac != null && ac instanceof IHTMLActionHandler )
				{
					actionHandler = (IHTMLActionHandler) ac;
				}
				if ( actionHandler != null )
				{
					link = actionHandler.getURL( act, context );
				}
				else
				{
					link = hlAction.getHyperlink( );
				}

				switch ( type )
				{
					case IHyperlinkAction.ACTION_BOOKMARK :
						//cache to create later
						hyperlinks.add(new Object[] {currentPage, link,bookmark,targetWindow,type,x,y,width,height});
						break;

					case IHyperlinkAction.ACTION_HYPERLINK :
						currentPage.createHyperlink( link, (String)null, targetWindow,
								type, x, y, width, height );
						break;

					case IHyperlinkAction.ACTION_DRILLTHROUGH :
						currentPage.createHyperlink( link, (String)null, targetWindow,
								type, x, y, width, height );
						break;
				}
			}
			catch ( Exception e )
			{
				logger.log( Level.WARNING, e.getMessage( ), e );
			}
	}

	protected void createBookmark( IArea area, int x, int y )
	{
		String bookmark = area.getBookmark( );
		if ( null != bookmark )
		{
			int height = getHeight( area );
			int width = getWidth( area );
			PdfDestination d = currentPage.createBookmark( bookmark, x, y, width, height );
			bookmarks.put( bookmark, d );
		}
	}

	private void createTOC( )
	{
		currentPageDevice.createTOC( bookmarks.keySet() );
	}

}
