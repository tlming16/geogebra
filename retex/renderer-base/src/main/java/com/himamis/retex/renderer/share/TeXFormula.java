/* TeXFormula.java
 * =========================================================================
 * This file is originally part of the JMathTeX Library - http://jmathtex.sourceforge.net
 *
 * Copyright (C) 2004-2007 Universiteit Gent
 * Copyright (C) 2009 DENIZET Calixte
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * A copy of the GNU General Public License can be found in the file
 * LICENSE.txt provided with the source distribution of this program (see
 * the META-INF directory in the source jar). This license can also be
 * found on the GNU website at http://www.gnu.org/licenses/gpl.html.
 *
 * If you did not receive a copy of the GNU General Public License along
 * with this program, contact the lead developer, or write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 *
 * Linking this library statically or dynamically with other modules 
 * is making a combined work based on this library. Thus, the terms 
 * and conditions of the GNU General Public License cover the whole 
 * combination.
 * 
 * As a special exception, the copyright holders of this library give you 
 * permission to link this library with independent modules to produce 
 * an executable, regardless of the license terms of these independent 
 * modules, and to copy and distribute the resulting executable under terms 
 * of your choice, provided that you also meet, for each linked independent 
 * module, the terms and conditions of the license of that module. 
 * An independent module is a module which is not derived from or based 
 * on this library. If you modify this library, you may extend this exception 
 * to your version of the library, but you are not obliged to do so. 
 * If you do not wish to do so, delete this exception statement from your 
 * version.
 * 
 */

/* Modified by Calixte Denizet */

package com.himamis.retex.renderer.share;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.himamis.retex.renderer.share.TeXConstants.Align;
import com.himamis.retex.renderer.share.TeXConstants.Muskip;
import com.himamis.retex.renderer.share.TeXLength.Unit;
import com.himamis.retex.renderer.share.character.Character;
import com.himamis.retex.renderer.share.cyrillic.CyrillicRegistration;
import com.himamis.retex.renderer.share.exception.FormulaNotFoundException;
import com.himamis.retex.renderer.share.exception.InvalidAtomTypeException;
import com.himamis.retex.renderer.share.exception.InvalidUnitException;
import com.himamis.retex.renderer.share.exception.ParseException;
import com.himamis.retex.renderer.share.exception.ResourceParseException;
import com.himamis.retex.renderer.share.greek.GreekRegistration;
import com.himamis.retex.renderer.share.platform.Graphics;
import com.himamis.retex.renderer.share.platform.Resource;
import com.himamis.retex.renderer.share.platform.graphics.Color;
import com.himamis.retex.renderer.share.platform.graphics.Graphics2DInterface;
import com.himamis.retex.renderer.share.platform.graphics.Image;
import com.himamis.retex.renderer.share.platform.graphics.Insets;

/**
 * Represents a logical mathematical formula that will be displayed (by creating a {@link TeXIcon}
 * from it and painting it) using algorithms that are based on the TeX algorithms.
 * <p>
 * These formula's can be built using the built-in primitive TeX parser (methods with String
 * arguments) or using other TeXFormula objects. Most methods have (an) equivalent(s) where one or
 * more TeXFormula arguments are replaced with String arguments. These are just shorter notations,
 * because all they do is parse the string(s) to TeXFormula's and call an equivalent method with (a)
 * TeXFormula argument(s). Most methods also come in 2 variants. One kind will use this TeXFormula
 * to build another mathematical construction and then change this object to represent the newly
 * build construction. The other kind will only use other TeXFormula's (or parse strings), build a
 * mathematical construction with them and insert this newly build construction at the end of this
 * TeXFormula. Because all the provided methods return a pointer to this (modified) TeXFormula
 * (except for the createTeXIcon method that returns a TeXIcon pointer), method chaining is also
 * possible.
 * <p>
 * <b> Important: All the provided methods modify this TeXFormula object, but all the TeXFormula
 * arguments of these methods will remain unchanged and independent of this TeXFormula object!</b>
 */
public class TeXFormula {

	public static final String VERSION = "1.0.3";

	public static final int SERIF = 0;
	public static final int SANSSERIF = 1;
	public static final int BOLD = 2;
	public static final int ITALIC = 4;
	public static final int ROMAN = 8;
	public static final int TYPEWRITER = 16;

	// point-to-pixel conversion
	final public static double PIXELS_PER_POINT = 1f;

	// for comparing doubles with 0
	protected static final double PREC = 0.0000001f;

	// predefined TeXFormula's
	public static Map<String, TeXFormula> predefinedTeXFormulas = new HashMap<String, TeXFormula>(150);
	public static Map<String, String> predefinedTeXFormulasAsString = new HashMap<String, String>(150);

	// character-to-symbol and character-to-delimiter mappings
	public static String[] symbolMappings = new String[65536];
	public static String[] symbolTextMappings = new String[65536];
	public static String[] symbolFormulaMappings = new String[65536];
	public static Map<Character.UnicodeBlock, FontInfos> externalFontMap = new HashMap<Character.UnicodeBlock, FontInfos>();

	public List<MiddleAtom> middle = new LinkedList<MiddleAtom>();

	protected Map<String, String> jlmXMLMap;

	static {
		// character-to-symbol and character-to-delimiter mappings
		TeXFormulaSettingsParser parser = new TeXFormulaSettingsParser();
		parser.parseSymbolMappings(symbolMappings, symbolTextMappings);

		new PredefinedCommands();
		new PredefinedTeXFormulas();
		new PredefMacros();

		parser.parseSymbolToFormulaMappings(symbolFormulaMappings, symbolTextMappings);

		try {
			//TeXFont.registerAlphabet((AlphabetRegistration) Class.forName(
			//		"com.himamis.retex.renderer.share.cyrillic.CyrillicRegistration").newInstance());
			//TeXFont.registerAlphabet((AlphabetRegistration) Class.forName(
			//		"com.himamis.retex.renderer.share.greek.GreekRegistration").newInstance());
			TeXFont.registerAlphabet(new CyrillicRegistration());
			TeXFont.registerAlphabet(new GreekRegistration());
		} catch (Exception e) {
		}

		// setDefaultDPI();
	}

	public static void addSymbolMappings(String file) throws ResourceParseException {
		Object in = new Resource().loadResource(file);
		addSymbolMappings(in, file);
	}

	public static void addSymbolMappings(Object in, String name) throws ResourceParseException {
		TeXFormulaSettingsParser tfsp = new TeXFormulaSettingsParser(in, name);
		tfsp.parseSymbolMappings(symbolMappings, symbolTextMappings);
		tfsp.parseSymbolToFormulaMappings(symbolFormulaMappings, symbolTextMappings);
	}

	public static boolean isRegisteredBlock(Character.UnicodeBlock block) {
		return externalFontMap.get(block) != null;
	}

	public static FontInfos getExternalFont(Character.UnicodeBlock block) {
		FontInfos infos = externalFontMap.get(block);
		if (infos == null) {
			infos = new FontInfos("SansSerif", "Serif");
			externalFontMap.put(block, infos);
		}

		return infos;
	}

	/**
	 * Set the DPI of target
	 * 
	 * @param dpi the target DPI
	 */
//	public static void setDPITarget(double dpi) {
//		PIXELS_PER_POINT = dpi / 72f;
//	}

	/**
	 * Set the default target DPI to the screen dpi (only if we're in non-headless mode)
	 */
	//public static void setDefaultDPI() {
		// TODO
		/*
		 * if (!GraphicsEnvironment.isHeadless()) { setDPITarget((double)
		 * Toolkit.getDefaultToolkit().getScreenResolution()); }
		 */
	//}

	// the root atom of the "atom tree" that represents the formula
	public Atom root = null;

	// the current text style
	public String textStyle = null;

	public boolean isColored = false;

	/**
	 * Creates an empty TeXFormula.
	 *
	 */
	public TeXFormula() {
		new TeXParser("", this, false);
	}

	/**
	 * Creates a new TeXFormula by parsing the given string (using a primitive
	 * TeX parser).
	 *
	 * @param s
	 *            the string to be parsed
	 * @param map
	 *            map
	 * @throws ParseException
	 *             if the string could not be parsed correctly
	 */
	public TeXFormula(String s, Map<String, String> map) throws ParseException {
		this.jlmXMLMap = map;
		TeXParser parser = new TeXParser(s, this);
		parser.parse();
	}

	/**
	 * Creates a new TeXFormula by parsing the given string (using a primitive TeX parser).
	 *
	 * @param s the string to be parsed
	 * @throws ParseException if the string could not be parsed correctly
	 */
	public TeXFormula(String s) throws ParseException {
		this(s, (String) null);
	}

	public TeXFormula(String s, boolean firstpass) throws ParseException {
		this.textStyle = null;
		TeXParser parser = new TeXParser(s, this, firstpass);
		parser.parse();
	}

	/*
	 * Creates a TeXFormula by parsing the given string in the given text style. Used when a text
	 * style command was found in the parse string.
	 */
	public TeXFormula(String s, String textStyle) throws ParseException {
		this.textStyle = textStyle;
		TeXParser parser = new TeXParser(s, this);
		parser.parse();
	}

	public TeXFormula(String s, String textStyle, boolean firstpass, boolean space) throws ParseException {
		this.textStyle = textStyle;
		TeXParser parser = new TeXParser(s, this, firstpass, space);
		parser.parse();
	}

	/**
	 * Creates a new TeXFormula that is a copy of the given TeXFormula.
	 * <p>
	 * <b>Both TeXFormula's are independent of one another!</b>
	 *
	 * @param f the formula to be copied
	 */
	public TeXFormula(TeXFormula f) {
		if (f != null) {
			addImpl(f);
		}
	}

	/**
	 * Creates an empty TeXFormula.
	 *
	 */
	protected TeXFormula(TeXParser tp) {
		this.jlmXMLMap = tp.formula.jlmXMLMap;
		new TeXParser(tp.getIsPartial(), "", this, false);
	}

	/**
	 * Creates a new TeXFormula by parsing the given string (using a primitive TeX parser).
	 *
	 * @param s the string to be parsed
	 * @throws ParseException if the string could not be parsed correctly
	 */
	protected TeXFormula(TeXParser tp, String s) throws ParseException {
		this(tp, s, null);
	}

	protected TeXFormula(TeXParser tp, String s, boolean firstpass) throws ParseException {
		this.textStyle = null;
		this.jlmXMLMap = tp.formula.jlmXMLMap;
		boolean isPartial = tp.getIsPartial();
		TeXParser parser = new TeXParser(isPartial, s, this, firstpass);
		if (isPartial) {
			try {
				parser.parse();
			} catch (Exception e) {
			}
		} else {
			parser.parse();
		}
	}

	/*
	 * Creates a TeXFormula by parsing the given string in the given text style. Used when a text
	 * style command was found in the parse string.
	 */
	protected TeXFormula(TeXParser tp, String s, String textStyle) throws ParseException {
		this.textStyle = textStyle;
		this.jlmXMLMap = tp.formula.jlmXMLMap;
		boolean isPartial = tp.getIsPartial();
		TeXParser parser = new TeXParser(isPartial, s, this);
		if (isPartial) {
			try {
				parser.parse();
			} catch (Exception e) {
				if (root == null) {
					root = new EmptyAtom();
				}
			}
		} else {
			parser.parse();
		}
	}

	protected TeXFormula(TeXParser tp, String s, String textStyle, boolean firstpass, boolean space)
			throws ParseException {
		this.textStyle = textStyle;
		this.jlmXMLMap = tp.formula.jlmXMLMap;
		boolean isPartial = tp.getIsPartial();
		TeXParser parser = new TeXParser(isPartial, s, this, firstpass, space);
		if (isPartial) {
			try {
				parser.parse();
			} catch (Exception e) {
				if (root == null) {
					root = new EmptyAtom();
				}
			}
		} else {
			parser.parse();
		}
	}

	public static TeXFormula getAsText(String text, Align alignment)
			throws ParseException {
		TeXFormula formula = new TeXFormula();
		if (text == null || "".equals(text)) {
			formula.add(new EmptyAtom());
			return formula;
		}

		String[] arr = text.split("\n|\\\\\\\\|\\\\cr");
		ArrayOfAtoms atoms = new ArrayOfAtoms();
		for (String s : arr) {
			TeXFormula f = new TeXFormula(s, "mathnormal", true, false);
			atoms.add(new RomanAtom(f.root));
			atoms.addRow();
		}
		atoms.checkDimensions();
		formula.add(new MatrixAtom(false, atoms, MatrixAtom.ARRAY, alignment));

		return formula;
	}

	/**
	 * @param formula
	 *            formula
	 * @return a partial TeXFormula containing the valid part of formula
	 */
	public static TeXFormula getPartialTeXFormula(String formula) {
		TeXFormula f = new TeXFormula();
		if (formula == null) {
			f.add(new EmptyAtom());
			return f;
		}
		TeXParser parser = new TeXParser(true, formula, f);
		try {
			parser.parse();
		} catch (Exception e) {
			if (f.root == null) {
				f.root = new EmptyAtom();
			}
		}

		return f;
	}

	// /**
	// * @param b true if the fonts should be registered (Java 1.6 only) to be
	// used with FOP.
	// */
	// public static void registerFonts(boolean b) {
	// TeXFontParser.registerFonts(b);
	// }

	/**
	 * Inserts an atom at the end of the current formula
	 * 
	 * @param el
	 *            atom
	 * @return new formula with atom
	 */
	public TeXFormula add(Atom el) {
		if (el != null) {
			if (el instanceof MiddleAtom) {
				middle.add((MiddleAtom) el);
			}
			if (root == null) {
				root = el;
			} else {
				if (!(root instanceof RowAtom)) {
					root = new RowAtom(root);
				}
				((RowAtom) root).add(el);
				if (el instanceof TypedAtom) {
					TypedAtom ta = (TypedAtom) el;
					int rtype = ta.getRightType();
					if (rtype == TeXConstants.TYPE_BINARY_OPERATOR || rtype == TeXConstants.TYPE_RELATION) {
						((RowAtom) root).add(new BreakMarkAtom());
					}
				}
			}
		}
		return this;
	}

	/**
	 * Parses the given string and inserts the resulting formula at the end of the current
	 * TeXFormula.
	 *
	 * @param s the string to be parsed and inserted
	 * @throws ParseException if the string could not be parsed correctly
	 * @return the modified TeXFormula
	 */
	public TeXFormula add(String s) throws ParseException {
		if (s != null && s.length() != 0) {
			// reset parsing variables
			textStyle = null;
			// parse and add the string
			add(new TeXFormula(s));
		}
		return this;
	}

	public TeXFormula append(String s) throws ParseException {
		return append(false, s);
	}

	public TeXFormula append(boolean isPartial, String s) throws ParseException {
		if (s != null && s.length() != 0) {
			TeXParser tp = new TeXParser(isPartial, s, this);
			tp.parse();
		}
		return this;
	}

	/**
	 * Inserts the given TeXFormula at the end of the current TeXFormula.
	 *
	 * @param f the TeXFormula to be inserted
	 * @return the modified TeXFormula
	 */
	public TeXFormula add(TeXFormula f) {
		addImpl(f);
		return this;
	}

	private void addImpl(TeXFormula f) {
		if (f.root != null) {
			// special copy-treatment for Mrow as a root!!
			if (f.root instanceof RowAtom) {
				add(new RowAtom(f.root));
			} else {
				add(f.root);
			}
		}
	}

	public void setLookAtLastAtom(boolean b) {
		if (root instanceof RowAtom) {
			((RowAtom) root).lookAtLastAtom = b;
		}
	}

	public boolean getLookAtLastAtom() {
		if (root instanceof RowAtom) {
			return ((RowAtom) root).lookAtLastAtom;
		}
		return false;
	}

	/**
	 * Centers the current TeXformula vertically on the axis (defined by the parameter "axisheight"
	 * in the resource "TeXFont.xml".
	 *
	 * @return the modified TeXFormula
	 */
	public TeXFormula centerOnAxis() {
		root = new VCenteredAtom(root);
		return this;
	}

	public static void addPredefinedTeXFormula(Object xmlFile) throws ResourceParseException {
		new PredefinedTeXFormulaParser(xmlFile, "TeXFormula").parse(predefinedTeXFormulas);
	}

	public static void addPredefinedCommands(Object xmlFile) throws ResourceParseException {
		new PredefinedTeXFormulaParser(xmlFile, "Command").parse(MacroInfo.Commands);
	}

	/**
	 * Inserts a strut box (whitespace) with the given width, height and depth (in the given unit)
	 * at the end of the current TeXFormula.
	 *
	 * @param unit a unit constant (from {@link TeXConstants})
	 * @param width the width of the strut box
	 * @param height the height of the strut box
	 * @param depth the depth of the strut box
	 * @return the modified TeXFormula
	 * @throws InvalidUnitException if the given integer value does not represent a valid unit
	 */
	public TeXFormula addStrut(Unit unit, double width, double height,
			double depth) throws InvalidUnitException {
		return add(new SpaceAtom(unit, width, height, depth));
	}

	/**
	 * Inserts a strut box (whitespace) with the given width, height and depth (in the given unit)
	 * at the end of the current TeXFormula.
	 *
	 * @param type thinmuskip, medmuskip or thickmuskip (from {@link TeXConstants})
	 * @return the modified TeXFormula
	 * @throws InvalidUnitException if the given integer value does not represent a valid unit
	 */
	public TeXFormula addStrut(Muskip type) throws InvalidUnitException {
		return add(new SpaceAtom(type));
	}

	/*
	 * Convert this TeXFormula into a box, starting form the given style
	 */
	private Box createBox(TeXEnvironment style) {
		if (root == null) {
			return new StrutBox(0, 0, 0, 0);
		}
		return root.createBox(style);
	}

	private static TeXFont createFont(double size, int type) {
		TeXFont dtf = new TeXFont(size);
		if (type == 0) {
			dtf.setSs(false);
		}
		if ((type & ROMAN) != 0) {
			dtf.setRoman(true);
		}
		if ((type & TYPEWRITER) != 0) {
			dtf.setTt(true);
		}
		if ((type & SANSSERIF) != 0) {
			dtf.setSs(true);
		}
		if ((type & ITALIC) != 0) {
			dtf.setIt(true);
		}
		if ((type & BOLD) != 0) {
			dtf.setBold(true);
		}

		return dtf;
	}

	/**
	 * Apply the Builder pattern instead of using the createTeXIcon(...) factories
	 * 
	 * @author Felix Natter
	 *
	 */
	public class TeXIconBuilder {
		private Integer style;
		private Double size;
		private Integer type;
		private Color fgcolor;
		private boolean trueValues = false;
		private Unit widthUnit;
		private Double textWidth;
		private Align align;
		private boolean isMaxWidth = false;
		private Unit interLineUnit;
		private Double interLineSpacing;

		/**
		 * Specify the style for rendering the given TeXFormula
		 * 
		 * @param style the style
		 * @return the builder, used for chaining
		 */
		public TeXIconBuilder setStyle(final int style) {
			this.style = style;
			return this;
		}

		/**
		 * Specify the font size for rendering the given TeXFormula
		 * 
		 * @param size the size
		 * @return the builder, used for chaining
		 */
		public TeXIconBuilder setSize(final double size) {
			this.size = size;
			return this;
		}

		/**
		 * Specify the font type for rendering the given TeXFormula
		 * 
		 * @param type the font type
		 * @return the builder, used for chaining
		 */
		public TeXIconBuilder setType(final int type) {
			this.type = type;
			return this;
		}

		/**
		 * Specify the background color for rendering the given TeXFormula
		 * 
		 * @param fgcolor the foreground color
		 * @return the builder, used for chaining
		 */
		public TeXIconBuilder setFGColor(final Color fgcolor) {
			this.fgcolor = fgcolor;
			return this;
		}

		/**
		 * Specify the "true values" parameter for rendering the given TeXFormula
		 * 
		 * @param trueValues the "true values" value
		 * @return the builder, used for chaining
		 */
		public TeXIconBuilder setTrueValues(final boolean trueValues) {
			this.trueValues = trueValues;
			return this;
		}

		/**
		 * Specify the width of the formula (may be exact or maximum width, see
		 * {@link #setIsMaxWidth(boolean)})
		 * 
		 * @param widthUnit the width unit
		 * @param textWidth the width
		 * @param align the alignment
		 * @return the builder, used for chaining
		 */
		public TeXIconBuilder setWidth(final Unit widthUnit,
				final double textWidth, final Align align) {
			this.widthUnit = widthUnit;
			this.textWidth = textWidth;
			this.align = align;
			trueValues = true; // TODO: is this necessary?
			return this;
		}

		/**
		 * Specifies whether the width is the exact or the maximum width
		 * 
		 * @param isMaxWidth whether the width is a maximum width
		 * @return the builder, used for chaining
		 */
		public TeXIconBuilder setIsMaxWidth(final boolean isMaxWidth) {
			if (widthUnit == null) {
				throw new IllegalStateException("Cannot set 'isMaxWidth' without having specified a width!");
			}
			if (isMaxWidth) {
				// NOTE: Currently isMaxWidth==true does not work with ALIGN_CENTER or ALIGN_RIGHT
				// (see HorizontalBox ctor)
				// The case (1) we don't support by setting align := ALIGN_LEFT here is this:
				// \text{hello world\\hello} with align=ALIGN_CENTER (but forced to ALIGN_LEFT) and
				// isMaxWidth==true results in:
				// [hello world]
				// [hello ]
				// and NOT:
				// [hello world]
				// [ hello ]
				// However, this case (2) is currently not supported anyway (ALIGN_CENTER with
				// isMaxWidth==false):
				// [ hello world ]
				// [ hello ]
				// and NOT:
				// [ hello world ]
				// [ hello ]
				// => until (2) is solved, we stick with the hack to set align := ALIGN_LEFT!
				this.align = TeXConstants.Align.LEFT;
			}
			this.isMaxWidth = isMaxWidth;
			return this;
		}

		/**
		 * Specify the inter line spacing unit and value. NOTE: this is required for automatic
		 * linebreaks to work!
		 * 
		 * @param interLineUnit the unit
		 * @param interLineSpacing the value
		 * @return the builder, used for chaining
		 */
		public TeXIconBuilder setInterLineSpacing(final Unit interLineUnit,
				final double interLineSpacing) {
			if (widthUnit == null) {
				throw new IllegalStateException(
						"Cannot set inter line spacing without having specified a width!");
			}
			this.interLineUnit = interLineUnit;
			this.interLineSpacing = interLineSpacing;
			return this;
		}

		/**
		 * Create a TeXIcon from the information gathered by the (chained) setXXX() methods. (see
		 * Builder pattern)
		 * 
		 * @return the TeXIcon
		 */
		public TeXIcon build() {
			if (style == null) {
				throw new IllegalStateException("A style is required. Use setStyle()");
			}
			if (size == null) {
				throw new IllegalStateException("A size is required. Use setStyle()");
			}
			TeXFont font = (type == null) ? new TeXFont(size) : createFont(size, type);
			TeXEnvironment te;
			if (widthUnit != null) {
				te = new TeXEnvironment(style, font, widthUnit, textWidth);
			} else {
				te = new TeXEnvironment(style, font);
			}

			if (interLineUnit != null) {
				te.setInterline(interLineUnit, interLineSpacing);
			}

			Box box = createBox(te);
			TeXIcon ti;
			if (widthUnit != null) {
				HorizontalBox hb;
				if (interLineUnit != null) {
					double il = interLineSpacing * SpaceAtom.getFactor(interLineUnit, te);
					Box b = BreakFormula.split(box, te.getTextwidth(), il);
					hb = new HorizontalBox(b, isMaxWidth ? b.getWidth() : te.getTextwidth(), align);
				} else {
					hb = new HorizontalBox(box, isMaxWidth ? box.getWidth() : te.getTextwidth(), align);
				}
				ti = new TeXIcon(hb, size, trueValues);
			} else {
				ti = new TeXIcon(box, size, trueValues);
			}
			if (fgcolor != null) {
				ti.setForeground(fgcolor);
			}
			ti.isColored = te.isColored;
			return ti;
		}
	}

	/**
	 * Creates a TeXIcon from this TeXFormula using the default TeXFont in the given point size and
	 * starting from the given TeX style. If the given integer value does not represent a valid TeX
	 * style, the default style TeXConstants.STYLE_DISPLAY will be used.
	 *
	 * @param style a TeX style constant (from {@link TeXConstants}) to start from
	 * @param size the default TeXFont's point size
	 * @return the created TeXIcon
	 */
	public TeXIcon createTeXIcon(int style, double size) {
		return new TeXIconBuilder().setStyle(style).setSize(size).build();
	}

	public TeXIcon createTeXIcon(int style, double size, int type) {
		return new TeXIconBuilder().setStyle(style).setSize(size).setType(type).build();
	}

	public TeXIcon createTeXIcon(int style, double size, int type, Color fgcolor) {
		return new TeXIconBuilder().setStyle(style).setSize(size).setType(type).setFGColor(fgcolor).build();
	}

	public TeXIcon createTeXIcon(int style, double size, boolean trueValues) {
		return new TeXIconBuilder().setStyle(style).setSize(size).setTrueValues(trueValues).build();
	}

	public TeXIcon createTeXIcon(int style, double size, Unit widthUnit,
			double textwidth, Align align) {
		return createTeXIcon(style, size, 0, widthUnit, textwidth, align);
	}

	public TeXIcon createTeXIcon(int style, double size, int type,
			Unit widthUnit, double textwidth, Align align) {
		return new TeXIconBuilder().setStyle(style).setSize(size).setType(type)
				.setWidth(widthUnit, textwidth, align).build();
	}

	public TeXIcon createTeXIcon(int style, double size, Unit widthUnit,
			double textwidth, Align align, Unit interlineUnit,
			double interline) {
		return createTeXIcon(style, size, 0, widthUnit, textwidth, align, interlineUnit, interline);
	}

	public TeXIcon createTeXIcon(int style, double size, int type,
			Unit widthUnit, double textwidth, Align align, Unit interlineUnit,
			double interline) {
		return new TeXIconBuilder().setStyle(style).setSize(size).setType(type)
				.setWidth(widthUnit, textwidth, align).setInterLineSpacing(interlineUnit, interline).build();
	}

//	public static void createImage(String format, int style, double size, String out, Color bg, Color fg,
//			boolean transparency) {
//		// TODO
//		throw new UnsupportedOperationException();
		/*TeXIcon icon = createTeXIcon(style, size);
		icon.setInsets(new Insets(1, 1, 1, 1));
		int w = icon.getIconWidth(), h = icon.getIconHeight();

		Image image = new Graphics().createImage(w, h, transparency ? Image.TYPE_INT_ARGB
				: Image.TYPE_INT_RGB);
		Graphics2DInterface g2 = image.createGraphics2D();
		if (bg != null && !transparency) {
			g2.setColor(bg);
			g2.fillRect(0, 0, w, h);
		}

		icon.setForeground(fg);
		icon.paintIcon(null, g2, 0, 0);*/
		/*
		 * try { FileImageOutputStream imout = new FileImageOutputStream(new File(out));
		 * ImageIO.write(image, format, imout); imout.flush(); imout.close(); } catch (IOException
		 * ex) { System.err.println("I/O error : Cannot generate " + out); }
		 * 
		 * g2.dispose();
		 */
	//}

//	public static void createPNG(int style, double size, String out, Color bg, Color fg) {
//		createImage("png", style, size, out, bg, fg, bg == null);
//	}
//
//	public static void createGIF(int style, double size, String out, Color bg, Color fg) {
//		createImage("gif", style, size, out, bg, fg, bg == null);
//	}
//
//	public static void createJPEG(int style, double size, String out, Color bg, Color fg) {
//		// There is a bug when a BufferedImage has a component alpha so we disabel it
//		createImage("jpeg", style, size, out, bg, fg, false);
//	}

	/**
	 * @param formula
	 *            the formula
	 * @param style
	 *            the style
	 * @param size
	 *            the size
	 * @param fg
	 *            foreground color
	 * @param bg
	 *            background color
	 * @return the generated image
	 * @throws ParseException
	 *             exception
	 */
	public static Image createBufferedImage(String formula, int style, double size, Color fg, Color bg)
			throws ParseException {
		TeXFormula f = new TeXFormula(formula);
		
		return f.createBufferedImage(style, size, fg, bg);
	}

	/**
	 * @param style
	 *            the style
	 * @param size
	 *            the size
	 * @param fg
	 *            foreground color
	 * @param bg
	 *            background color
	 * @return the generated image
	 * @throws ParseException
	 *             exception
	 */
	public Image createBufferedImage(int style, double size, Color fg, Color bg) throws ParseException {
		TeXIcon icon = createTeXIcon(style, size);
		icon.setInsets(new Insets(2, 2, 2, 2));
		int w = icon.getIconWidth(), h = icon.getIconHeight();

		Image image = new Graphics().createImage(w, h, bg == null ? Image.TYPE_INT_ARGB : Image.TYPE_INT_RGB);
		Graphics2DInterface g2 = image.createGraphics2D();
		if (bg != null) {
			g2.setColor(bg);
			g2.fillRect(0, 0, w, h);
		}

		icon.setForeground(fg == null ? ColorUtil.BLACK : fg);
		icon.paintIcon(null, g2, 0, 0);
		g2.dispose();

		return image;
	}

	/**
	 * Changes the background color of the <i>current</i> TeXFormula into the given color. By
	 * default, a TeXFormula has no background color, it's transparent. The backgrounds of
	 * subformula's will be painted on top of the background of the whole formula! Any changes that
	 * will be made to this TeXFormula after this background color was set, will have the default
	 * background color (unless it will also be changed into another color afterwards)!
	 *
	 * @param c the desired background color for the <i>current</i> TeXFormula
	 * @return the modified TeXFormula
	 */
	public TeXFormula setBackground(Color c) {
		if (c != null) {
			if (root instanceof ColorAtom) {
				root = new ColorAtom(c, null, (ColorAtom) root);
			} else {
				root = new ColorAtom(root, c, null);
			}
		}
		return this;
	}

	/**
	 * Changes the (foreground) color of the <i>current</i> TeXFormula into the given color. By
	 * default, the foreground color of a TeXFormula is the foreground color of the component on
	 * which the TeXIcon (created from this TeXFormula) will be painted. The color of subformula's
	 * overrides the color of the whole formula. Any changes that will be made to this TeXFormula
	 * after this color was set, will be painted in the default color (unless the color will also be
	 * changed afterwards into another color)!
	 *
	 * @param c the desired foreground color for the <i>current</i> TeXFormula
	 * @return the modified TeXFormula
	 */
	public TeXFormula setColor(Color c) {
		if (c != null) {
			if (root instanceof ColorAtom) {
				root = new ColorAtom(null, c, (ColorAtom) root);
			} else {
				root = new ColorAtom(root, null, c);
			}
		}
		return this;
	}

	/**
	 * Sets a fixed left and right type of the current TeXFormula. This has an influence on the glue
	 * that will be inserted before and after this TeXFormula.
	 *
	 * @param leftType atom type constant (from {@link TeXConstants})
	 * @param rightType atom type constant (from TeXConstants)
	 * @return the modified TeXFormula
	 * @throws InvalidAtomTypeException if the given integer value does not represent a valid atom
	 *         type
	 */
	public TeXFormula setFixedTypes(int leftType, int rightType) throws InvalidAtomTypeException {
		root = new TypedAtom(leftType, rightType, root);
		return this;
	}

	/**
	 * Get a predefined TeXFormula.
	 *
	 * @param name the name of the predefined TeXFormula
	 * @return a copy of the predefined TeXFormula
	 * @throws FormulaNotFoundException if no predefined TeXFormula is found with the given name
	 */
	public static TeXFormula get(String name) throws FormulaNotFoundException {
		TeXFormula formula = predefinedTeXFormulas.get(name);
		if (formula == null) {
			String f = predefinedTeXFormulasAsString.get(name);
			if (f == null) {
				throw new FormulaNotFoundException(name);
			}
			TeXFormula tf = new TeXFormula(f);
			if (!(tf.root instanceof RowAtom)) {
				// depending of the context a RowAtom can be modified
				// so we can't reuse it
				predefinedTeXFormulas.put(name, tf);
			}
			return tf;
		}
		return new TeXFormula(formula);
	}

	static class FontInfos {

		String sansserif;
		String serif;

		FontInfos(String sansserif, String serif) {
			this.sansserif = sansserif;
			this.serif = serif;
		}
	}

	// XXX
	public int getTextStyle() {
		return TextStyle.getStyle(textStyle);
	}
}
