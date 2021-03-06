/* ResizeAtom.java
 * =========================================================================
 * This file is part of the JLaTeXMath Library - http://forge.scilab.org/jlatexmath
 *
 * Copyright (C) 2011 DENIZET Calixte
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

package com.himamis.retex.renderer.share;

import com.himamis.retex.renderer.share.TeXLength.Unit;

/**
 * An atom representing a scaled Atom.
 */
public class ResizeAtom extends Atom {

	private Atom base;
	private Unit wunit, hunit;
	private double w, h;
	private boolean keepaspectratio;

	@Override
	final public Atom duplicate() {
		ResizeAtom ret = new ResizeAtom();
		ret.base = base;
		ret.wunit = wunit;
		ret.hunit = hunit;
		ret.w=w;
		ret.h=h;
		ret.keepaspectratio = keepaspectratio;
		
		return setFields(ret);
	}

	public ResizeAtom(Atom base, String ws, String hs, boolean keepaspectratio) {
		this.type = base.type;
		this.base = base;
		this.keepaspectratio = keepaspectratio;
		Object[] w = SpaceAtom.getLength(ws == null ? "" : ws);
		Object[] h = SpaceAtom.getLength(hs == null ? "" : hs);
		if (w.length != 2) {
			this.wunit = Unit.NONE;
		} else {
			this.wunit = (Unit) w[0];
			this.w = (Double) w[1];
		}
		if (h.length != 2) {
			this.hunit = Unit.NONE;
		} else {
			this.hunit = (Unit) h[0];
			this.h = (Double) h[1];
		}
	}

	public ResizeAtom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public int getLeftType() {
		return base.getLeftType();
	}

	@Override
	public int getRightType() {
		return base.getRightType();
	}

	@Override
	public Box createBox(TeXEnvironment env) {
		Box bbox = base.createBox(env);
		if (wunit == Unit.NONE && hunit == Unit.NONE) {
			return bbox;
		}
		double xscl = 1;
		double yscl = 1;
		if (wunit != Unit.NONE && hunit != Unit.NONE) {
			xscl = w * SpaceAtom.getFactor(wunit, env) / bbox.width;
			yscl = h * SpaceAtom.getFactor(hunit, env) / bbox.height;
			if (keepaspectratio) {
				xscl = Math.min(xscl, yscl);
				yscl = xscl;
			}
		} else if (wunit != Unit.NONE && hunit == Unit.NONE) {
			xscl = w * SpaceAtom.getFactor(wunit, env) / bbox.width;
			yscl = xscl;
		} else {
			yscl = h * SpaceAtom.getFactor(hunit, env) / bbox.height;
			xscl = yscl;
		}

		return new ScaleBox(bbox, xscl, yscl);
	}
}
