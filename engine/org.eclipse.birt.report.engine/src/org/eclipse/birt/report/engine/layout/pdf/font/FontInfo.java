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

package org.eclipse.birt.report.engine.layout.pdf.font;

import org.eclipse.birt.report.engine.emitter.EmitterUtil;
import org.eclipse.birt.report.engine.layout.PDFConstants;

import com.itextpdf.io.font.constants.FontStyles;
import com.itextpdf.kernel.font.PdfFont;

public class FontInfo
{
	private PdfFont bf;

	private float fontSize;

	private int fontStyle;

	private int fontWeight;

	private boolean simulation;

	private float lineWidth;
	private float fontHeight;
	private float baselinePosition;
	private float underlinePosition;
	private float linethroughPosition;
	private float overlinePosition;

	public FontInfo( PdfFont bf, float fontSize, int fontStyle, int fontWeight,
			boolean simulation )
	{
		this.bf = bf;
		this.fontStyle = fontStyle;
		this.simulation = simulation;
		this.fontSize = fontSize;
		this.fontWeight = fontWeight;
		setupFontSize( );
	}

	public FontInfo( FontInfo fontInfo )
	{
		this.bf = fontInfo.bf;
		this.fontStyle = fontInfo.fontStyle;
		this.simulation = fontInfo.simulation;
		this.fontSize = fontInfo.fontSize;
		this.fontWeight = fontInfo.fontWeight;
		setupFontSize( );
	}

	public void setFontSize( float fontSize )
	{
		this.fontSize = fontSize;
		setupFontSize( );
	}

	protected void setupFontSize( )
	{

		if ( bf == null )
		{
			lineWidth = 1;
			fontHeight = fontSize;
			baselinePosition = fontSize;
			underlinePosition = fontSize;
			linethroughPosition = fontSize / 2;
			overlinePosition = 0;
			return;
		}

		float ascent = (float) (bf.getFontProgram().getFontMetrics().getAscender()/ 1000.0) * fontSize;
		float descent = (float) (bf.getFontProgram().getFontMetrics().getDescender( )/ 1000.0) * fontSize;
		
		if (ascent == 0 ) {
			ascent = (float) (bf.getFontProgram().getFontMetrics().getBbox()[3] / 1000.0) * fontSize;
			descent = (float) (bf.getFontProgram().getFontMetrics().getBbox()[1]/ 1000.0) * fontSize;
		}
		
		if ( descent > 0 ) descent = -descent;
		
		float strike = (float) (bf.getFontProgram().getFontMetrics().getStrikeoutPosition()/ 1000.0) * fontSize;
		float strike_thickness = (float) (bf.getFontProgram().getFontMetrics().getStrikeoutSize()/ 1000.0) * fontSize;
		
		float baseline = (float) (bf.getFontProgram().getFontMetrics().getUnderlinePosition() / 1000.0) * fontSize;
		float baseline_thickness = (float) (bf.getFontProgram().getFontMetrics().getUnderlineThickness() / 1000.0) * fontSize;

		lineWidth = baseline_thickness;
		if ( lineWidth == 0 )
		{
			lineWidth = strike_thickness;
			if ( lineWidth == 0 )
			{
				lineWidth = fontSize / 20;
			}
		}
		fontHeight = ascent - descent;
		//TODO: the -lineWidth/2 should be move to the draw function
		baselinePosition = ascent - lineWidth / 2;
		underlinePosition = ascent - baseline - lineWidth / 2;
		if ( strike == 0 )
		{
			linethroughPosition =  ascent+ descent - lineWidth / 2;
		}
		else
		{
			linethroughPosition = ascent - strike - lineWidth / 2;
		}
		//TODO: overline is not same with the HTML, we need change it in future.
		overlinePosition = 0;
	}

	public void setSimulation( boolean simulation )
	{
		this.simulation = simulation;
	}

	public PdfFont getBaseFont( )
	{
		return this.bf;
	}

	public float getFontSize( )
	{
		return this.fontSize;
	}

	public int getFontStyle( )
	{
		return this.fontStyle;
	}

	public int getFontWeight( )
	{
		return this.fontWeight;
	}

	public boolean getSimulation( )
	{
		return this.simulation;
	}

	public float getLineWidth( )
	{
		return this.lineWidth;
	}

	public int getOverlinePosition( )
	{
		return (int) ( overlinePosition * PDFConstants.LAYOUT_TO_PDF_RATIO );
	}

	public int getUnderlinePosition( )
	{
		return (int) ( underlinePosition * PDFConstants.LAYOUT_TO_PDF_RATIO );
	}

	public int getLineThroughPosition( )
	{
		return (int) ( linethroughPosition * PDFConstants.LAYOUT_TO_PDF_RATIO );
	}

	public int getBaseline( )
	{
		return (int) ( baselinePosition * PDFConstants.LAYOUT_TO_PDF_RATIO );
	}

	/**
	 * Gets the width of the specified word.
	 * 
	 * @param word
	 *            the word
	 * @return the points of the width
	 */
	public float getWordWidth( String word )
	{
		if ( word == null )
		{
			return 0;
		}
		if ( bf == null )
		{
			return word.length( ) * ( fontSize / 2 );
		}

		return bf.getWidth( word, fontSize );
	}
	
	public int getItalicAdjust( )
	{
		// get width for text with simulated italic font.
		if ( simulation && ( FontStyles.ITALIC == fontStyle
				|| FontStyles.BOLDITALIC == fontStyle ) )
		{
			return (int) ( fontHeight
					* EmitterUtil.getItalicHorizontalCoefficient( ) );
		}
		return 0;
	}

	/**
	 * Gets the height of the specified word.
	 * 
	 * @return the height of the font, it equals ascent+|descent|+leading
	 */
	public float getWordHeight( )
	{
		return fontHeight;
	}

	public String getFontName( )
	{
		assert bf != null;
		
		String[][] familyFontNames = bf.getFontProgram().getFontNames().getFamilyName();
		String[] family = familyFontNames[familyFontNames.length - 1];
		return family[family.length - 1];
	}
}