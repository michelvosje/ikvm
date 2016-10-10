/*
  Copyright (C) 2009, 2011 Volker Berlin (i-net software)

  This software is provided 'as-is', without any express or implied
  warranty.  In no event will the authors be held liable for any damages
  arising from the use of this software.

  Permission is granted to anyone to use this software for any purpose,
  including commercial applications, and to alter it and redistribute it
  freely, subject to the following restrictions:

  1. The origin of this software must not be misrepresented; you must not
     claim that you wrote the original software. If you use this software
     in a product, an acknowledgment in the product documentation would be
     appreciated but is not required.
  2. Altered source versions must be plainly marked as such, and must not be
     misrepresented as being the original software.
  3. This notice may not be removed or altered from any source distribution.

  Jeroen Frijters
  jeroen@frijters.net

 */
package sun.font;

import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.util.Locale;

import cli.System.Drawing.Font;
import ikvm.internal.NotYetImplementedError;


/**
 *
 */
public class CompositeFont extends Font2D{

	private final PhysicalFont delegate;

	int numSlots;
	int numMetricsSlots;
	int[] exclusionRanges;
	int[] maxIndices;


    public CompositeFont(PhysicalFont physicalFont, CompositeFont dialog2d) {
    	delegate = physicalFont;
	}

	public CompositeFont(Font2D font2d){
		delegate = (PhysicalFont)font2d;
	}

    public int getNumSlots() {
    	throw new NotYetImplementedError();
    }

    public PhysicalFont getSlotFont(int slot) {
    	if( slot == 0){
    		return delegate;
    	}
        throw new NotYetImplementedError();
    }

	public boolean isStdComposite() {
		throw new NotYetImplementedError();
	}

	@Override
    public int getStyle(){
		return delegate.getStyle();
    }

    @Override
    public Font createNetFont(java.awt.Font font){
        return delegate.createNetFont(font);
    }

	@Override
  public FontStrike getStrike(java.awt.Font font, AffineTransform devTx,
			int aa, int fm) {
		return delegate.getStrike(font, devTx, aa, fm);
	}

	@Override
  public FontStrike getStrike(java.awt.Font font, FontRenderContext frc) {
		return delegate.getStrike(font, frc);
	}

	@Override
  public void removeFromCache(FontStrikeDesc desc) {
		delegate.removeFromCache(desc);
	}

	@Override
  public void getFontMetrics(java.awt.Font font, AffineTransform identityTx,
			Object antiAliasingHint, Object fractionalMetricsHint,
			float[] metrics) {
		delegate.getFontMetrics(font, identityTx, antiAliasingHint,
				fractionalMetricsHint, metrics);
	}

        public boolean isExcludedChar(int slot, int charcode) {

          if (exclusionRanges == null || maxIndices == null ||
            slot >= numMetricsSlots) {
	      return false;
          }

          int minIndex = 0;
          int maxIndex = maxIndices[slot];
          if (slot > 0) {
            minIndex = maxIndices[slot - 1];
          }
          int curIndex = minIndex;
          while (maxIndex > curIndex) {
            if ((charcode >= exclusionRanges[curIndex])
              && (charcode <= exclusionRanges[curIndex+1])) {
                return true;      // excluded
              }
              curIndex += 2;
            }
            return false;
	  }

	@Override
  public void getStyleMetrics(float pointSize, float[] metrics, int offset) {
		delegate.getStyleMetrics(pointSize, metrics, offset);
	}

	@Override
  public void getFontMetrics(java.awt.Font font, FontRenderContext frc,
			float[] metrics) {
		delegate.getFontMetrics(font, frc, metrics);
	}

	@Override
  public boolean useAAForPtSize(int ptsize) {
		return delegate.useAAForPtSize(ptsize);
	}

	@Override
  public CharToGlyphMapper getMapper() {
	    if (mapper == null) {
	        mapper = new CompositeGlyphMapper(this);
	    }
	    return mapper;
	}

	@Override
  public boolean hasSupplementaryChars() {
		return delegate.hasSupplementaryChars();
	}

	@Override
  public String getPostscriptName() {
		return delegate.getPostscriptName();
	}

	@Override
  public String getFontName(Locale l) {
		return delegate.getFontName(l);
	}

	@Override
  public String getFamilyName(Locale l) {
		return delegate.getFamilyName(l);
	}

	@Override
  public int getNumGlyphs() {
		return delegate.getNumGlyphs();
	}

	@Override
  public int charToGlyph(int wchar) {
		return delegate.charToGlyph(wchar);
	}

	@Override
  public int getMissingGlyphCode() {
		return delegate.getMissingGlyphCode();
	}

	@Override
  public boolean canDisplay(char c) {
		return delegate.canDisplay(c);
	}

	@Override
  public boolean canDisplay(int cp) {
		return delegate.canDisplay(cp);
	}

	@Override
  public byte getBaselineFor(char c) {
		return delegate.getBaselineFor(c);
	}

	@Override
  public float getItalicAngle(java.awt.Font font, AffineTransform at,
			Object aaHint, Object fmHint) {
		return delegate.getItalicAngle(font, at, aaHint, fmHint);
	}

  @Override
  FontStrike createStrike(FontStrikeDesc desc)
  {
    return new CompositeStrike(this, desc);
  }
}
