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

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.OperationNotSupportedException;

import org.eclipse.birt.report.engine.content.IHyperlinkAction;
import org.eclipse.birt.report.engine.content.IStyle;
import org.eclipse.birt.report.engine.emitter.EmitterUtil;
import org.eclipse.birt.report.engine.layout.emitter.AbstractPage;
import org.eclipse.birt.report.engine.layout.pdf.font.FontInfo;
import org.eclipse.birt.report.engine.nLayout.area.style.BackgroundImageInfo;
import org.eclipse.birt.report.engine.nLayout.area.style.BorderInfo;
import org.eclipse.birt.report.engine.nLayout.area.style.TextStyle;
import org.w3c.dom.css.CSSValue;

import com.itextpdf.io.font.constants.FontStyles;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfAnnotationBorder;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfTextArray;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfSquareAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants.TextRenderingMode;
import com.itextpdf.kernel.pdf.navigation.PdfDestination;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;

public class PDFPage extends AbstractPage
{


	protected static Logger logger = Logger
			.getLogger( PDFPage.class.getName( ) );

	protected float containerHeight;

	protected PDFPageDevice pageDevice;
	protected PdfPage page;
	protected PdfCanvas canvas;
	/**
	 * font size must greater than minimum font . if not,illegalArgumentException
	 * will be thrown.
	 */
	private static float MIN_FONT_SIZE = 1.0E-4f;
	
	//set of rectangles where total page number needs to be written
	private Set<Rectangle> totalPage = new HashSet<>();
	
	private static Pattern PAGE_LINK_PATTERN = Pattern
			.compile( "^((([a-zA-Z]:))(/(\\w[\\w ]*.*))+\\.(pdf|PDF))+#page=(\\d+)$" );

	public PDFPage( int pageWidth, int pageHeight, PDFPageDevice pageDevice, PdfDocument document)
	{
		super( pageWidth, pageHeight );

		this.pageDevice = pageDevice;
		this.containerHeight = this.pageHeight;
		
		document.setDefaultPageSize( new PageSize(this.pageWidth, this.pageHeight) );
		
		this.page = document.addNewPage();
		this.canvas = new PdfCanvas(page);
		
	}

	protected void clip( float startX, float startY, float width, float height )
	{

		startY = transformY( startY, height );
		canvas.moveTo(startX, startY);
		canvas.lineTo(startX + width, startY);
		canvas.lineTo(startX + width, startY + height);
		canvas.lineTo(startX , startY + height);
		canvas.clip( );
		canvas.endPath();
	}

	protected void restoreState( )
	{
		canvas.restoreState( );
	}

	protected void saveState( )
	{
		canvas.saveState( );
	}

	public void dispose( )
	{
	}

	protected void drawBackgroundColor( java.awt.Color color, float x, float y,
			float width, float height )
	{
		if ( null == color )
		{
			return;
		}
		y = transformY( y, height );
		
		canvas.saveState();
		canvas.setFillColor(new DeviceRgb(color));
		canvas.rectangle(x, y, width, height);
		canvas.fill();
		canvas.restoreState();
	}
	
	//deprecated
	@Override
	protected void drawImage(String uri, String extension, float imageX, float imageY, float height, float width,
			String helpText, Map params) throws Exception {
		throw new OperationNotSupportedException();
	}

	@Override
	protected void drawBackgroundImage( float x, float y, float width,
			float height, float imageWidth, float imageHeight, int repeat,
			String imageUrl, byte[] imageData, float offsetX, float offsetY )
			throws Exception
	{
		
		canvas.saveState();

		clip( x, y, width, height );
		
		ImageData image = null;
		if ( imageUrl != null )
		{
			if ( pageDevice.getImageCache( ).containsKey( imageUrl ) )
			{
				image = pageDevice.getImageCache( ).get( imageUrl );
			}
		}
		if ( image == null )
		{
			
			image = ImageDataFactory.create(imageData);
			if ( imageUrl != null && image != null )
			{
				pageDevice.getImageCache( ).put( imageUrl, image );
			}
		}

		boolean xExtended = ( repeat & BackgroundImageInfo.REPEAT_X ) == BackgroundImageInfo.REPEAT_X;
		boolean yExtended = ( repeat & BackgroundImageInfo.REPEAT_Y ) == BackgroundImageInfo.REPEAT_Y;
		imageWidth = image.getWidth( );
		imageHeight = image.getHeight( );

		float originalX = offsetX;
		float originalY = offsetY;
		if ( xExtended )
		{
			while ( originalX > 0 )
				originalX -= imageWidth;
		}
		if ( yExtended )
		{
			while ( originalY > 0 )
				originalY -= imageHeight;
		}

		float startY = originalY;
		do
		{
			float startX = originalX;
			do
			{
				drawImage( image, x + startX, y + startY, imageWidth, imageHeight );
				startX += imageWidth;
			} while ( startX < width && xExtended );
			startY += imageHeight;
		} while ( startY < height && yExtended );
		canvas.restoreState( );
	}

	protected void drawImage( String imageId, byte[] iData,
			String extension, float imageX, float imageY, float height,
			float width, String helpText, Map params ) throws Exception
	{
		// Cached Image
		ImageData imageData = null;
		if ( imageId != null )
		{
			if ( pageDevice.getImageCache( ).containsKey( imageId ) )
			{
				imageData = pageDevice.getImageCache( ).get( imageId );
			}
			if ( imageData != null )
			{
				drawImage( imageData, imageX, imageY, height, width, helpText );
				return;
			}
		}

		// Not cached yet
//		if ( SvgFile.isSvg( null, null, extension ) )
//		{
//			//TODO:
//			imageData = generateTemplateFromSVG( null, iData, imageX,
//					imageY, height, width, helpText );
//		}
//		else
//		{
			// PNG/JPG/BMP... images:
		imageData = ImageDataFactory.create( iData );
//		}
		// Cache the image
		if ( imageId != null && imageData != null )
		{
			pageDevice.getImageCache( ).put( imageId, imageData );
		}
		if ( imageData != null )
		{
			drawImage( imageData, imageX, imageY, height, width, helpText );
		}
	}


	/**
	 * Draws a line with the line-style specified in advance from the start
	 * position to the end position with the given line width, color, and style
	 * at the given PDF layer. If the line-style is NOT set before invoking this
	 * method, "solid" will be used as the default line-style.
	 *
	 * @param startX
	 *            the start X coordinate of the line.
	 * @param startY
	 *            the start Y coordinate of the line.
	 * @param endX
	 *            the end X coordinate of the line.
	 * @param endY
	 *            the end Y coordinate of the line.
	 * @param width
	 *            the lineWidth
	 * @param color
	 *            the color of the line.
	 * @param lineStyle
	 *            the style of the line.
	 */
	protected void drawLine( float startX, float startY, float endX,
			float endY, float width, java.awt.Color color, int lineStyle )
	{
		// if the border does NOT have color or the line width of the border is
		// zero or the lineStyle is "none", just return.
		if ( null == color || 0f == width
				|| BorderInfo.BORDER_STYLE_NONE == lineStyle ) //$NON-NLS-1$
		{
			return;
		}
		canvas.saveState( );
		if ( BorderInfo.BORDER_STYLE_SOLID == lineStyle ) //$NON-NLS-1$
		{
			drawRawLine( startX, startY, endX, endY, width, color, canvas );
		}
		else if ( BorderInfo.BORDER_STYLE_DASHED == lineStyle ) //$NON-NLS-1$
		{
			canvas.setLineDash( 3 * width, 2 * width, 0f );
			drawRawLine( startX, startY, endX, endY, width, color, canvas );
		}
		else if ( BorderInfo.BORDER_STYLE_DOTTED == lineStyle ) //$NON-NLS-1$
		{
			canvas.setLineDash( width, width, 0f );
			drawRawLine( startX, startY, endX, endY, width, color, canvas );
		}
		else if ( BorderInfo.BORDER_STYLE_DOUBLE == lineStyle ) //$NON-NLS-1$
		{
			return;
		}
		// the other line styles, e.g. 'ridge', 'outset', 'groove', 'inset' is
		// NOT supported now.
		// We look it as the default line style -- 'solid'
		else
		{
			drawRawLine( startX, startY, endX, endY, width, color, canvas );
		}
		canvas.restoreState( );
	}

	protected void drawText( String text, float textX, float textY,
			float baseline, float width, float height, TextStyle textStyle )
	{
		drawText( text, textX, textY + baseline, width, height,
				textStyle.getFontInfo( ),
				convertToPoint( textStyle.getLetterSpacing( ) ),
				convertToPoint( textStyle.getWordSpacing( ) ),
				textStyle.getColor( ), textStyle.isLinethrough( ),
				textStyle.isOverline( ), textStyle.isUnderline( ),
				textStyle.getAlign( ) );
		if ( textStyle.isHasHyperlink( ) )
		{
			FontInfo fontInfo = textStyle.getFontInfo( );
			float lineWidth = fontInfo.getLineWidth( );
			java.awt.Color color = textStyle.getColor( );
			drawDecorationLine( textX, textY, width, lineWidth,
					convertToPoint( fontInfo.getUnderlinePosition( ) ), color );
		}
	}

	private void drawText( String text, float textX, float textY, float width,
			float height, FontInfo fontInfo, float characterSpacing,
			float wordSpacing, java.awt.Color color, boolean linethrough,
			boolean overline, boolean underline, CSSValue align )
	{
		drawText( text, textX, textY, fontInfo, characterSpacing, wordSpacing,
				color, align );
	}

	public void drawTotalPage( String text, TextStyle textInfo )
	{
		for (Rectangle r : totalPage) {
			drawText(text, (int)r.getX(), (int)r.getY(), (int)r.getWidth(), (int)r.getHeight(), textInfo);
		}
	}

	public PdfDestination createBookmark( String bookmark, int x, int y, int width,
			int height )
	{
		
		return createBookmark( bookmark, convertToPoint( x ), convertToPoint( y ),
				convertToPoint( width ), convertToPoint( height ) );
	}

	private PdfDestination createBookmark( String bookmark, float x, float y,
			float width, float height )
	{
		PdfDestination add = PdfExplicitDestination.createXYZ(page, x, transformY(y), 1.0f);
		page.getDocument().getOutlines(false).addDestination(add);
		return add;
	}

	public void createHyperlink( String hyperlink, PdfDestination bookmark,
			String targetWindow, int type, int x, int y, int width, int height )
	{
		PdfAction action = PdfAction.createGoTo(bookmark);
		
		float x1 = convertToPoint(x);
		float y1 = convertToPoint(y);
		float width1 = convertToPoint(width);
		float height1 = convertToPoint(height);
		
		y1 = transformY( y1, height1 );
		
		PdfLinkAnnotation annotation = new PdfLinkAnnotation(new Rectangle(x1,y1,width1,height1));
		annotation.setAction(action);
		annotation.setBorder(new PdfAnnotationBorder(0,0,0));		
		page.addAnnotation(annotation);
	}

	
	public void createHyperlink( String hyperlink, String bookmark,
			String targetWindow, int type, int x, int y, int width, int height )
	{
		createHyperlink( hyperlink, bookmark, targetWindow, type,
				convertToPoint( x ), convertToPoint( y ),
				convertToPoint( width ), convertToPoint( height ) );
	}

	private void createHyperlink( String hyperlink, String bookmark,
			String targetWindow, int type, float x, float y, float width,
			float height )
	{
		PdfAction action = createPdfAction(hyperlink, bookmark, targetWindow, type);
		
		y = transformY( y, height );
		
		PdfLinkAnnotation annotation = new PdfLinkAnnotation(new Rectangle(x,y,width,height));
		annotation.setAction(action);
		annotation.setBorder(new PdfAnnotationBorder(0,0,0));		
		page.addAnnotation(annotation);
	}

	public void createTotalPageTemplate( int x, int y, int width, int height)
	{
		//cache area for writing later
		totalPage.add(new Rectangle(x,y,width,height));
	}

	/**
	 * Draws a line with the line-style specified in advance from the start
	 * position to the end position with the given linewidth, color, and style
	 * at the given pdf layer. If the line-style is NOT set before invoking this
	 * method, "solid" will be used as the default line-style.
	 *
	 * @param startX
	 *            the start X coordinate of the line
	 * @param startY
	 *            the start Y coordinate of the line
	 * @param endX
	 *            the end X coordinate of the line
	 * @param endY
	 *            the end Y coordinate of the line
	 * @param width
	 *            the lineWidth
	 * @param color
	 *            the color of the line
	 * @param contentByte
	 *            the given pdf layer
	 */
	private void drawRawLine( float startX, float startY, float endX,
			float endY, float width, java.awt.Color color, PdfCanvas canvas )
	{
		startY = transformY( startY );
		endY = transformY( endY );
		canvas.moveTo( startX, startY );
		canvas.lineTo( endX, endY );

		canvas.setLineWidth( width );
		canvas.setStrokeColor( new DeviceRgb(color) );
		canvas.stroke( );
	}

	private void drawText( String text, float textX, float textY,
			FontInfo fontInfo, float characterSpacing, float wordSpacing,
			java.awt.Color color, CSSValue align )
	{
		canvas.saveState( );
		// start drawing the text content
		canvas.beginText( );
		if ( null != color && !java.awt.Color.BLACK.equals( color ) )
		{
			canvas.setFillColor( new DeviceRgb(color) );
			canvas.setStrokeColor( new DeviceRgb(color) );
		}
		PdfFont font = null;
		try
		{
			font = PdfFontFactory.createRegisteredFont( fontInfo.getFontName() );
		}catch (Exception ex) {
			logger.log(Level.WARNING, ex.getMessage());
			try {
				font = PdfFontFactory.createFont(StandardFonts.TIMES_ROMAN);
			} catch (IOException e) {
				logger.log(Level.SEVERE, ex.getMessage());
				return;
			}
		}
		
		float fontSize = fontInfo.getFontSize( );
		
		try
		{
			canvas.setFontAndSize( font, fontSize );
		}
		catch ( IllegalArgumentException e )
		{
			logger.log( Level.WARNING, e.getMessage( ) );
			// close to zero , increase by one MIN_FONT_SIZE step
			canvas.setFontAndSize( font, MIN_FONT_SIZE * 2 );
		}
		if ( characterSpacing != 0 )
		{
			canvas.setCharacterSpacing( characterSpacing );
		}
		if ( wordSpacing != 0 )
		{
			canvas.setWordSpacing( wordSpacing );
		}
		
		
		setTextMatrix( canvas, fontInfo, textX,
				transformY( textY, 0, containerHeight ) );
		
		if ( (font.getFontProgram().getFontIdentification().getTtfVersion()  != null)
//		if ( ( font.getFontType( ) == BaseFont.FONT_TYPE_TTUNI )
				&& IStyle.JUSTIFY_VALUE.equals( align ) && wordSpacing > 0 )
		{
			int idx = text.indexOf( ' ' );
			if ( idx >= 0 )
			{
				float spaceCorrection = -wordSpacing * 1000 / fontSize;
				
				PdfTextArray textArray = new PdfTextArray();
				textArray.add( text.substring( 0, idx ), font );
				
				int lastIdx = idx;
				while ( ( idx = text.indexOf( ' ', lastIdx + 1 ) ) >= 0 )
				{
					textArray.add( spaceCorrection );
					textArray.add( text.substring( lastIdx, idx ), font );
					lastIdx = idx;
				}
				textArray.add( spaceCorrection );
				textArray.add( text.substring( lastIdx ), font );
				canvas.showText( textArray );
			}
			else
			{
				canvas.showText( text );
			}
		}
		else
		{
			canvas.showText( text );
		}
		
		canvas.endText( );
		canvas.restoreState( );
	}

	protected PdfFont getBaseFont( FontInfo fontInfo )
	{
		return fontInfo.getBaseFont( );
	}

	/**
	 * Creates a PdfAction.
	 *
	 * @param hyperlink
	 *            the hyperlink.
	 * @param bookmark
	 *            the bookmark.
	 * @param target
	 *            if target equals "_blank", the target will be opened in a new
	 *            window, else the target will be opened in the current window.
	 * @return the created PdfAction.
	 */
	private PdfAction createPdfAction( String hyperlink, String bookmark,
			String target, int type )
	{
		// patch from Ales Novy
		if ( "_top".equalsIgnoreCase( target )
				|| "_parent".equalsIgnoreCase( target )
				|| "_blank".equalsIgnoreCase( target )
				|| "_self".equalsIgnoreCase( target ) )
		// Opens the target in a new window.
		{
			if ( hyperlink == null )
				hyperlink = "";
			boolean isUrl = hyperlink.startsWith( "http" );
			if ( !isUrl )
			{
				Matcher matcher = PAGE_LINK_PATTERN.matcher( hyperlink );
				if ( matcher.find( ) )
				{
					String fileName = matcher.group( 1 );
					String pageNumber = matcher.group( matcher.groupCount( ) );
					PdfAction.createGoToR(fileName,
							Integer.valueOf( pageNumber ) );
				}
			}
			return PdfAction.createURI( hyperlink );
		}
		else

		// Opens the target in the current window.
		{
			if ( type == IHyperlinkAction.ACTION_BOOKMARK )
			{
				return PdfAction.createGoTo( bookmark );
			}
			else
			{
				return PdfAction.createGoToR(hyperlink, bookmark, false);
			}
		}
	}

	private void setTextMatrix( PdfCanvas cb, FontInfo fi, float x, float y )
	{
		cb.concatMatrix( 1, 0, 0, 1, x, y );
		if ( !fi.getSimulation( ) )
		{
			cb.setTextMatrix( 0, 0 );
			return;
		}
		switch ( fi.getFontStyle( ) )
		{
			case FontStyles.ITALIC :
			{
				simulateItalic( cb );
				break;
			}
			case FontStyles.BOLD :
			{
				simulateBold( cb, fi.getFontWeight( ) );
				break;
			}
			case FontStyles.BOLDITALIC :
			{
				simulateBold( cb, fi.getFontWeight( ) );
				simulateItalic( cb );
				break;
			}
		}
	}

	static HashMap<Integer, Float>fontWeightLineWidthMap = new HashMap<Integer, Float>( );
	static
	{
		fontWeightLineWidthMap.put( 500, 0.1f );
		fontWeightLineWidthMap.put( 600, 0.185f );
		fontWeightLineWidthMap.put( 700, 0.225f );
		fontWeightLineWidthMap.put( 800, 0.3f );
		fontWeightLineWidthMap.put( 900, 0.5f );
	};

	private void simulateBold( PdfCanvas cb, int fontWeight )
	{
		cb.setTextRenderingMode( TextRenderingMode.FILL_STROKE );
		if ( fontWeightLineWidthMap.containsKey( fontWeight ) )
		{
			cb.setLineWidth( fontWeightLineWidthMap.get( fontWeight ) );
		}
		else
		{
			cb.setLineWidth( 0.225f );
		}
		cb.setTextMatrix( 0, 0 );
	}

	private void simulateItalic( PdfCanvas cb )
	{
		float beta = EmitterUtil.ITALIC_HORIZONTAL_COEFFICIENT;
		cb.setTextMatrix( 1, 0, beta, 1, 0, 0 );
	}

	public void showHelpText( String helpText, float x, float y, float width,
			float height )
	{
		showHelpText( x, transformY( y, height ), width, height, helpText );
	}

	protected void showHelpText( float x, float y, float width, float height,
			String helpText )
	{
		Rectangle rectangle = new Rectangle( x, y, x + width, y + height );
		
		
		PdfAnnotation annotation = new PdfSquareAnnotation(rectangle);
		annotation.setContents(helpText);
//				
//		PdfBorderDictionary borderStyle = new PdfBorderDictionary( 0,
//				PdfBorderDictionary.STYLE_SOLID, null );
//		annotation.setBorderStyle( borderStyle );
		
		annotation.setFlags( 288 );
		
		page.addAnnotation(annotation);
	}

//	protected void drawImage( PdfCanvas image, float imageX, float imageY,
//			float height, float width, String helpText )
//			throws DocumentException
//	{
//		imageY = transformY( imageY, height );
//		contentByte.saveState( );
//		contentByte.concatCTM( 1, 0, 0, 1, imageX, imageY );
//		float w = image.getWidth( );
//		float h = image.getHeight( );
//		contentByte.addTemplate( image, width / w, 0f / w, 0f / h, height / h,
//				0f, 0f );
//		if ( helpText != null )
//		{
//			showHelpText( imageX, imageY, width, height, helpText );
//		}
//		contentByte.restoreState( );
//	}
//	
	private void drawImage( ImageData imageData, float imageX, float imageY,
			float width, float height )
	{
		drawImage( imageData, imageX, imageY, height, width, null );
	}

	protected void drawImage( ImageData imageData, float imageX, float imageY,
			float height, float width, String helpText )
	{
		imageY = transformY( imageY, height );
		canvas.saveState( );
		canvas.concatMatrix( 1, 0, 0, 1, imageX, imageY );
		
		canvas.addImageFittedIntoRectangle(imageData, new Rectangle(0,0,width,height), false);
		
		if ( helpText != null )
		{
			showHelpText( imageX, imageY, width, height, helpText );
		}
		canvas.restoreState( );
	}


//	protected ImageData generateTemplateFromSVG( String svgPath,
//			byte[] svgData, float x, float y, float height, float width,
//			String helpText ) throws Exception
//	{
//		return transSVG( null, svgData, x, y, height, width, helpText );
//	}
//
//	protected ImageData transSVG( String svgPath, byte[] svgData, float x,
//			float y, float height, float width, String helpText )
//			throws IOException
//	{
//		ByteArrayOutputStream ostream = new ByteArrayOutputStream( );
//		TranscoderOutput output = new TranscoderOutput( ostream );
//		
//		PrintTranscoder transcoder = new PrintTranscoder( );
//		if ( null != svgData && svgData.length > 0 )
//		{
//			transcoder.transcode( new TranscoderInput(
//					new ByteArrayInputStream( svgData ) ), output );
//		}
//		else if ( null != svgPath )
//		{
//			transcoder.transcode( new TranscoderInput( svgPath ), output );
//		}
//		
//		ostream.flush();
//		return ImageDataFactory.create(ostream.toByteArray());
//
//	}

	


}
