/*
  Copyright (C) 2009 Volker Berlin (i-net software)

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

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import sun.awt.SunHints;



/**
 *
 */
public abstract class Font2D{

    protected int style = Font.PLAIN;

    /*
     * A mapper can be independent of the strike.
     * Perhaps the reference to the mapper ought to be held on the
     * scaler, as it may be implemented via scaler functionality anyway
     * and so the mapper would be useless if its native portion was
     * freed when the scaler was GC'd.
     */
    protected CharToGlyphMapper mapper;

    public Font2DHandle handle = new Font2DHandle(this);

    /*
     * The strike cache is maintained per "Font2D" as that is the
     * principal object by which you look up fonts.
     * It means more Hashmaps, but look ups can be quicker because
     * the map will have fewer entries, and there's no need to try to
     * make the Font2D part of the key.
     */
    protected ConcurrentHashMap<FontStrikeDesc, Reference>
        strikeCache = new ConcurrentHashMap<FontStrikeDesc, Reference>();

    /* Store the last Strike in a Reference object.
     * Similarly to the strike that was stored on a C++ font object,
     * this is an optimisation which helps if multiple clients (ie
     * typically SunGraphics2D instances) are using the same font, then
     * as may be typical of many UIs, they are probably using it in the
     * same style, so it can be a win to first quickly check if the last
     * strike obtained from this Font2D satifies the needs of the next
     * client too.
     * This pre-supposes that a FontStrike is a shareable object, which
     * it should.
     */
    protected Reference lastFontStrike = new SoftReference(null);

    abstract CharToGlyphMapper getMapper();

    /*
     * Creates an appropriate strike for the Font2D subclass
     */
    abstract FontStrike createStrike(FontStrikeDesc desc);

    /* SunGraphics2D has font, tx, aa and fm. From this info
     * can get a Strike object from the cache, creating it if necessary.
     * This code is designed for multi-threaded access.
     * For that reason it creates a local FontStrikeDesc rather than filling
     * in a shared one. Up to two AffineTransforms and one FontStrikeDesc will
     * be created by every lookup. This appears to perform more than
     * adequately. But it may make sense to expose FontStrikeDesc
     * as a parameter so a caller can use its own.
     * In such a case if a FontStrikeDesc is stored as a key then
     * we would need to use a private copy.
     *
     * Note that this code doesn't prevent two threads from creating
     * two different FontStrike instances and having one of the threads
     * overwrite the other in the map. This is likely to be a rare
     * occurrence and the only consequence is that these callers will have
     * different instances of the strike, and there'd be some duplication of
     * population of the strikes. However since users of these strikes are
     * transient, then the one that was overwritten would soon be freed.
     * If there is any problem then a small synchronized block would be
     * required with its attendant consequences for MP scalability.
     */
    public FontStrike getStrike(Font font, AffineTransform devTx, int aa, int fm){
        return getStrike(font, new FontRenderContext(devTx, aa == SunHints.INTVAL_TEXT_ANTIALIAS_ON,
                fm == SunHints.INTVAL_FRACTIONALMETRICS_ON));
    }

    public FontStrike getStrike(Font font, FontRenderContext frc) {
        // TODO Auto-generated method stub
        return null;
    }

    FontStrike getStrike(FontStrikeDesc desc) {
        return getStrike(desc, true);
    }

    private FontStrike getStrike(FontStrikeDesc desc, boolean copy) {
        /* Before looking in the map, see if the descriptor matches the
         * last strike returned from this Font2D. This should often be a win
         * since its common for the same font, in the same size to be
         * used frequently, for example in many parts of a UI.
         *
         * If its not the same then we use the descriptor to locate a
         * Reference to the strike. If it exists and points to a strike,
         * then we update the last strike to refer to that and return it.
         *
         * If the key isn't in the map, or its reference object has been
         * collected, then we create a new strike, put it in the map and
         * set it to be the last strike.
         */
        FontStrike strike = (FontStrike)lastFontStrike.get();
        if (strike != null && desc.equals(strike.desc)) {
            //strike.lastlookupTime = System.currentTimeMillis();
            return strike;
        } else {
            Reference strikeRef = strikeCache.get(desc);
            if (strikeRef != null) {
                strike = (FontStrike)strikeRef.get();
                if (strike != null) {
                    //strike.lastlookupTime = System.currentTimeMillis();
                    lastFontStrike = new SoftReference(strike);
                    StrikeCache.refStrike(strike);
                    return strike;
                }
            }
            /* When we create a new FontStrike instance, we *must*
             * ask the StrikeCache for a reference. We must then ensure
             * this reference remains reachable, by storing it in the
             * Font2D's strikeCache map.
             * So long as the Reference is there (reachable) then if the
             * reference is cleared, it will be enqueued for disposal.
             * If for some reason we explicitly remove this reference, it
             * must only be done when holding a strong reference to the
             * referent (the FontStrike), or if the reference is cleared,
             * then we must explicitly "dispose" of the native resources.
             * The only place this currently happens is in this same method,
             * where we find a cleared reference and need to overwrite it
             * here with a new reference.
             * Clearing the whilst holding a strong reference, should only
             * be done if the
             */
            if (copy) {
                desc = new FontStrikeDesc(desc);
            }
            strike = createStrike(desc);
            //StrikeCache.addStrike();
            /* If we are creating many strikes on this font which
             * involve non-quadrant rotations, or more general
             * transforms which include shears, then force the use
             * of weak references rather than soft references.
             * This means that it won't live much beyond the next GC,
             * which is what we want for what is likely a transient strike.
             */
            int txType = desc.glyphTx.getType();
            if (txType == AffineTransform.TYPE_GENERAL_TRANSFORM ||
                (txType & AffineTransform.TYPE_GENERAL_ROTATION) != 0 &&
                strikeCache.size() > 10) {
                strikeRef = StrikeCache.getStrikeRef(strike, true);
            } else {
                strikeRef = StrikeCache.getStrikeRef(strike);
            }
            strikeCache.put(desc, strikeRef);
            //strike.lastlookupTime = System.currentTimeMillis();
            lastFontStrike = new SoftReference(strike);
            StrikeCache.refStrike(strike);
            return strike;
        }
    }

    public void removeFromCache(FontStrikeDesc desc){
        // TODO Auto-generated method stub

    }

    /**
     * The length of the metrics array must be >= 8.  This method will
     * store the following elements in that array before returning:
     *    metrics[0]: ascent
     *    metrics[1]: descent
     *    metrics[2]: leading
     *    metrics[3]: max advance
     *    metrics[4]: strikethrough offset
     *    metrics[5]: strikethrough thickness
     *    metrics[6]: underline offset
     *    metrics[7]: underline thickness
     */
    public void getFontMetrics(Font font, AffineTransform identityTx, Object antiAliasingHint,
            Object fractionalMetricsHint, float[] metrics){
        FontRenderContext frc = new FontRenderContext(identityTx, antiAliasingHint, fractionalMetricsHint);
        StrikeMetrics strikeMetrics = getStrike(font, frc).getFontMetrics();
        metrics[0] = strikeMetrics.getAscent();
        metrics[1] = strikeMetrics.getDescent();
        metrics[2] = strikeMetrics.getLeading();
        metrics[3] = strikeMetrics.getMaxAdvance();

        getStyleMetrics(font.getSize2D(), metrics, 4);
    }

    /**
     * The length of the metrics array must be >= offset+4, and offset must be
     * >= 0.  Typically offset is 4.  This method will
     * store the following elements in that array before returning:
     *    metrics[off+0]: strikethrough offset
     *    metrics[off+1]: strikethrough thickness
     *    metrics[off+2]: underline offset
     *    metrics[off+3]: underline thickness
     *
     * Note that this implementation simply returns default values;
     * subclasses can override this method to provide more accurate values.
     */
    public void getStyleMetrics(float pointSize, float[] metrics, int offset) {
        metrics[offset] = -metrics[0] / 2.5f;
        metrics[offset+1] = pointSize / 12;
        metrics[offset+2] = metrics[offset+1] / 1.5f;
        metrics[offset+3] = metrics[offset+1];
    }

    /**
     * The length of the metrics array must be >= 4.  This method will
     * store the following elements in that array before returning:
     *    metrics[0]: ascent
     *    metrics[1]: descent
     *    metrics[2]: leading
     *    metrics[3]: max advance
     */
    public void getFontMetrics(Font font, FontRenderContext frc,
                               float metrics[]) {
        StrikeMetrics strikeMetrics = getStrike(font, frc).getFontMetrics();
        metrics[0] = strikeMetrics.getAscent();
        metrics[1] = strikeMetrics.getDescent();
        metrics[2] = strikeMetrics.getLeading();
        metrics[3] = strikeMetrics.getMaxAdvance();
    }

    /*
     * All the important subclasses override this which is principally for
     * the TrueType 'gasp' table.
     */
    public boolean useAAForPtSize(int ptsize) {
        return true;
    }

    public boolean hasSupplementaryChars() {
        return false;
    }

    /* The following methods implement public methods on java.awt.Font */
    public String getPostscriptName(){
        // TODO Auto-generated method stub
        return null;
    }

    public String getFontName(Locale l){
        // TODO Auto-generated method stub
        return null;
    }

    public String getFamilyName(Locale l){
        // TODO Auto-generated method stub
        return null;
    }

    public int getNumGlyphs(){
        // TODO Auto-generated method stub
        return 0;
    }

    public int charToGlyph(int wchar) {
        return wchar;
    }

    public int getMissingGlyphCode(){
        // TODO Auto-generated method stub
        return 0;
    }

    public boolean canDisplay(char c){
        //HACK There is no equivalent in C# http://msdn2.microsoft.com/en-us/library/sf4dhbw8(VS.80).aspx
        return true;
    }

    public boolean canDisplay(int cp){
        //HACK There is no equivalent in C# http://msdn2.microsoft.com/en-us/library/sf4dhbw8(VS.80).aspx
        return true;
    }

    public byte getBaselineFor(char c) {
        return Font.ROMAN_BASELINE;
    }

    public float getItalicAngle(Font font, AffineTransform at,
                                Object aaHint, Object fmHint) {
        /* hardwire psz=12 as that's typical and AA vs non-AA for 'gasp' mode
         * isn't important for the caret slope of this rarely used API.
         */
        int aa = FontStrikeDesc.getAAHintIntVal(aaHint, this, 12);
        int fm = FontStrikeDesc.getFMHintIntVal(fmHint);
        FontStrike strike = getStrike(font, at, aa, fm);
        StrikeMetrics metrics = strike.getFontMetrics();
        if (metrics.ascentY == 0 || metrics.ascentX == 0) {
            return 0f;
        } else {
            /* ascent is "up" from the baseline so its typically
             * a negative value, so we need to compensate
             */
            return metrics.ascentX/-metrics.ascentY;
        }
    }

    /** Returns the "real" style of this Font2D. Eg the font face
     * Lucida Sans Bold" has a real style of Font.BOLD, even though
     * it may be able to used to simulate bold italic
     */
    public abstract int getStyle();

    public abstract cli.System.Drawing.Font createNetFont(Font font);

}
