/* 
GeoGebra - Dynamic Mathematics for Everyone
http://www.geogebra.org

This file is part of GeoGebra.

This program is free software; you can redistribute it and/or modify it 
under the terms of the GNU General Public License as published by 
the Free Software Foundation.

 */

package org.geogebra.common.euclidian.draw;

import org.geogebra.common.awt.GBasicStroke;
import org.geogebra.common.awt.GColor;
import org.geogebra.common.awt.GFont;
import org.geogebra.common.awt.GGraphics2D;
import org.geogebra.common.awt.GPoint2D;
import org.geogebra.common.awt.GRectangle;
import org.geogebra.common.euclidian.BoundingBox;
import org.geogebra.common.euclidian.Drawable;
import org.geogebra.common.euclidian.EuclidianBoundingBoxHandler;
import org.geogebra.common.euclidian.EuclidianStatic;
import org.geogebra.common.euclidian.EuclidianView;
import org.geogebra.common.euclidian.TextController;
import org.geogebra.common.factories.AwtFactory;
import org.geogebra.common.kernel.Matrix.Coords;
import org.geogebra.common.kernel.geos.GeoElement;
import org.geogebra.common.kernel.geos.GeoText;
import org.geogebra.common.kernel.kernelND.GeoPointND;
import org.geogebra.common.main.App;
import org.geogebra.common.main.Feature;
import org.geogebra.common.util.DoubleUtil;

/**
 * Drawable representation of text
 * 
 * @author Markus
 */
public final class DrawText extends Drawable {

	private static final int MIN_EDITOR_WIDTH = 100;
	/**
	 * color used to draw rectangle around text when highlighted
	 */
	public static final GColor HIGHLIGHT_COLOR = GColor.LIGHT_GRAY;
	private static final GColor EDITOR_BORDER_COLOR = GColor.GRAY;

	// private static final int SELECTION_DIAMETER_ADD = 4;
	// private static final int SELECTION_OFFSET = SELECTION_DIAMETER_ADD / 2;

	private GeoText text;
	private boolean isVisible;
	private boolean isLaTeX;
	private int fontSize = -1;
	private int fontStyle = -1;
	private boolean serifFont;
	private GFont textFont;
	private GeoPointND loc; // text location

	// private Image eqnImage;
	private int oldXpos;
	private int oldYpos;
	private boolean needsBoundingBoxOld;

	private BoundingBox boundingBox;
	private static GBasicStroke rectangleStroke = AwtFactory.getPrototype()
			.newBasicStroke(2);
	private TextController ctrl;

	/**
	 * Creates new DrawText
	 * 
	 * @param view
	 *            view
	 * @param text
	 *            text
	 */
	public DrawText(EuclidianView view, GeoText text) {
		this.view = view;
		this.text = text;
		geo = text;
		ctrl = view.getEuclidianController().getTextController();

		textFont = view.getApplication().getPlainFontCommon()
				.deriveFont(GFont.PLAIN, view.getFontSize());

		// this is needed as (bold) LaTeX texts are created with isLaTeX = false
		// at this stage
		updateStrokes(text);

		update();
	}

	@Override
	public void update() {
		isVisible = geo.isEuclidianVisible();
		if (!isVisible && !text.isNeedsUpdatedBoundingBox()) {
			// Corner[Element[text
			return;
		}

		if (isLaTeX) {
			updateStrokes(text);
		}

		if (isLaTeX) {
			updateStrokes(text);
		}

		String newText;
		if (geo.getKernel().getApplication().has(Feature.MOW_TEXT_TOOL) && ctrl != null) {
			newText = ctrl.wrapText(text.getTextString());
		} else {
			newText = text.getTextString();
		}
		boolean textChanged = labelDesc == null || !labelDesc.equals(newText)
				|| isLaTeX != text.isLaTeX()
				|| text.isNeedsUpdatedBoundingBox() != needsBoundingBoxOld;
		labelDesc = newText;
		isLaTeX = text.isLaTeX();
		needsBoundingBoxOld = text.isNeedsUpdatedBoundingBox();

		// compute location of text
		if (text.isAbsoluteScreenLocActive()) {
			xLabel = text.getAbsoluteScreenLocX();
			yLabel = text.getAbsoluteScreenLocY();
		} else {
			loc = text.getStartPoint();
			if (loc == null) {
				xLabel = (int) view.getXZero();
				yLabel = (int) view.getYZero();
			} else {
				if (!loc.isDefined()) {
					isVisible = false;
					return;
				}

				// looks if it's on view
				Coords p = view.getCoordsForView(loc.getInhomCoordsInD3());
				if (!DoubleUtil.isZero(p.getZ())) {
					isVisible = false;
					return;
				}

				xLabel = view.toScreenCoordX(p.getX());
				yLabel = view.toScreenCoordY(p.getY());
			}
			xLabel += text.labelOffsetX;
			yLabel += text.labelOffsetY;

			text.setTotalWidth((int) labelRectangle.getWidth());
			text.setTotalHeight((int) labelRectangle.getHeight());

		}

		boolean positionChanged = xLabel != oldXpos || yLabel != oldYpos;
		oldXpos = xLabel;
		oldYpos = yLabel;

		boolean fontChanged = doUpdateFontSize();

		// some commented code for LaTeX speedup removed in r22321

		// We need check for null bounding box because of
		// SetValue[text,Text["a",(1,1)]] makes it null
		if (text.isNeedsUpdatedBoundingBox() && (textChanged || positionChanged
				|| fontChanged || text.getKernel().getForceUpdatingBoundingBox()
				|| text.getBoundingBox() == null)) {
			// ensure that bounding box gets updated by drawing text once
			if (isLaTeX) {
				drawMultilineLaTeX(view.getTempGraphics2D(textFont), textFont,
						geo.getObjectColor(), view.getBackgroundCommon());
			} else {
				drawMultilineText(view.getTempGraphics2D(textFont), textFont);
			}

			// update corners for Corner[] command
			double xRW = view.toRealWorldCoordX(labelRectangle.getX());
			double yRW = view.toRealWorldCoordY(labelRectangle.getY());

			text.setBoundingBox(xRW, yRW,
					labelRectangle.getWidth() * view.getInvXscale(),
					-labelRectangle.getHeight() * view.getInvYscale());
		}

		if (isWhiteboardText() && boundingBox != null) {
			boundingBox.setRectangle(getBounds());
		}

		if (geo.getKernel().getApplication().has(Feature.MOW_TEXT_TOOL)) {
			ctrl.setEditorFont(textFont);
			ctrl.setEditorLineHeight(textFont.getSize() * 1.5);
			ctrl.setEditorColor(geo.getObjectColor());
		}

	}

	@Override
	public void draw(GGraphics2D g2) {
		if (isVisible) {
			GColor bg = geo.getBackgroundColor();

			if (bg != null) {

				// needed to calculate labelRectangle
				if (isLaTeX) {
					drawMultilineLaTeX(view.getTempGraphics2D(textFont),
							textFont, geo.getObjectColor(),
							view.getBackgroundCommon());
				} else {
					drawMultilineText(view.getTempGraphics2D(textFont),
							textFont);
				}
				g2.setStroke(objStroke);
				g2.setPaint(bg);
				g2.fill(labelRectangle);
			}

			if (isLaTeX) {
				g2.setPaint(geo.getObjectColor());
				g2.setFont(textFont);
				g2.setStroke(objStroke); // needed eg for \sqrt
				drawMultilineLaTeX(g2, textFont, geo.getObjectColor(),
						bg != null ? bg : view.getBackgroundCommon());
			} else {
				if (text.isEditMode()) {
					g2.setStroke(rectangleStroke);
					g2.setPaint(EDITOR_BORDER_COLOR);
					GRectangle rect = getBounds();
					g2.draw(rect);
				} else {
					g2.setPaint(geo.getObjectColor());
					drawMultilineText(g2, textFont);
				}

			}

			// draw label rectangle
			if (geo.doHighlighting()) {
				g2.setStroke(rectangleStroke);
				g2.setPaint(HIGHLIGHT_COLOR);
				g2.draw(labelRectangle);
			}

		}

	}

	public static int getMinEditorWidth() {
		return MIN_EDITOR_WIDTH;
	}

	/**
	 * Adjust bounding box to editor/text bounds.
	 */
	public void adjustBoundingBoxToText() {
		GRectangle rect = null;
		if (ctrl != null) {
			rect = ctrl.getEditorBounds();
		}

		if (rect == null || labelRectangle == null) {
			return;
		}
		labelRectangle.setBounds(rect);
		if (boundingBox != null) {
			boundingBox.setRectangle(labelRectangle);
		}
	}

	/**
	 * was this object clicked at? (mouse pointer location (x,y) in screen
	 * coords)
	 */
	@Override
	public boolean hit(int x, int y, int hitThreshold) {
		return super.hitLabel(x, y);
	}

	@Override
	public boolean isInside(GRectangle rect) {
		return rect.contains(labelRectangle);
	}

	@Override
	public boolean intersectsRectangle(GRectangle rect) {
		return labelRectangle.intersects(rect);
	}

	/**
	 * Returns false
	 */
	@Override
	public boolean hitLabel(int x, int y) {
		return false;
	}

	@Override
	public GeoElement getGeoElement() {
		return geo;
	}

	@Override
	public void setGeoElement(GeoElement geo) {
		this.geo = geo;
	}

	private boolean doUpdateFontSize() {
		// text's font size is relative to the global font size
		int newFontSize = (int) Math.max(4,
				view.getFontSize() * text.getFontSizeMultiplier());
		int newFontStyle = text.getFontStyle();
		boolean newSerifFont = text.isSerifFont();

		if (text.getTextString() != null
				&& textFont.canDisplayUpTo(text.getTextString()) != -1
				|| fontSize != newFontSize || fontStyle != newFontStyle
				|| newSerifFont != serifFont) {
			super.updateFontSize();

			fontSize = newFontSize;
			fontStyle = newFontStyle;
			serifFont = newSerifFont;

			// if (isLaTeX) {
			// //setEqnFontSize();
			// } else {
			App app = view.getApplication();
			textFont = app.getFontCanDisplay(text.getTextString(), serifFont,
					fontStyle, fontSize);
			// }
			return true;
		}

		return false;
	}

	/**
	 * Returns the bounding box of this Drawable in screen coordinates.
	 */
	@Override
	public GRectangle getBounds() {
		if (!geo.isDefined() || !geo.isEuclidianVisible()) {
			return null;
		}
		if (text.isEditMode() && labelRectangle.getWidth() < MIN_EDITOR_WIDTH) {
			labelRectangle.setBounds((int) labelRectangle.getX(),
					(int) labelRectangle.getY(), MIN_EDITOR_WIDTH,
					(int) labelRectangle.getHeight());
		}
		return labelRectangle;
	}

	@Override
	public BoundingBox getBoundingBox() {
		if (isWhiteboardText()) {
			if (boundingBox == null) {
				boundingBox = new BoundingBox(false, false);
				boundingBox.setRectangle(getBounds());
			}
			return boundingBox;
		}
		return null;
	}

	private boolean isWhiteboardText() {
		return view.getApplication().has(Feature.MOW_TEXT_TOOL);
	}

	@Override
	public void updateByBoundingBoxResize(GPoint2D point, EuclidianBoundingBoxHandler handler) {
		double minX = labelRectangle.getMinX();
		double maxX = labelRectangle.getMaxX();
		double minY = labelRectangle.getMinY();
		double maxY = labelRectangle.getMaxY();
		double mouseY = point.getY();
		double mouseX = point.getX();
		GeoPointND startPoint;
		int h;
		switch (handler) {
		case TOP:
			if ((maxY - mouseY) < text.getTextHeight()) {
				return;
			}
			startPoint = text.getStartPoint();
			startPoint.setCoords(startPoint.getInhomX(), view.toRealWorldCoordY(mouseY), 1.0);
			h = (int) (maxY - mouseY + fontSize + EuclidianStatic.EDITOR_MARGIN);
			labelRectangle.setSize((int) labelRectangle.getWidth(), h);
			break;
		case BOTTOM:
			if ((mouseY - minY) < text.getTextHeight()) {
				return;
			}
			h = (int) (mouseY - minY + EuclidianStatic.EDITOR_MARGIN);
			labelRectangle.setSize((int) labelRectangle.getWidth(), h);
			break;
		case RIGHT:
			if ((mouseX - minX) < MIN_EDITOR_WIDTH) {
				return;
			}
			labelRectangle.setSize((int) (mouseX - minX + EuclidianStatic.EDITOR_MARGIN),
					(int) labelRectangle.getHeight());
			break;
		case LEFT:
			if ((maxX - mouseX) < MIN_EDITOR_WIDTH) {
				return;
			}
			labelRectangle.setSize((int) (maxX - mouseX + EuclidianStatic.EDITOR_MARGIN),
					(int) labelRectangle.getHeight());
			startPoint = text.getStartPoint();
			startPoint.setCoords(view.toRealWorldCoordX(mouseX), startPoint.getInhomY(), 1.0);
			break;
		default:
			break;
		}
		text.update();
	}
}