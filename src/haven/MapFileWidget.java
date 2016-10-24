/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.util.*;
import java.util.function.*;
import java.awt.event.KeyEvent;
import haven.MapFile.Segment;
import haven.MapFile.Grid;
import haven.MapFile.GridInfo;
import static haven.MCache.cmaps;
import static haven.Utils.or;

public class MapFileWidget extends Widget {
    public final MapFile file;
    public Location curloc;
    private Locator setloc;
    private boolean follow;
    private Area dext;
    private Segment dseg;
    private DisplayGrid[] display;
    private UI.Grab drag;
    private boolean dragging;
    private Coord dsc, dmc;

    public MapFileWidget(MapFile file, Coord sz) {
	super();
	this.file = file;
	resize(sz);
    }

    public static class Location {
	public final Segment seg;
	public final Coord tc;

	public Location(Segment seg, Coord tc) {
	    Objects.requireNonNull(seg);
	    Objects.requireNonNull(tc);
	    this.seg = seg; this.tc = tc;
	}
    }

    public interface Locator {
	Location locate(MapFile file) throws Loading;
    }

    public static class MapLocator implements Locator {
	public final MapView mv;

	public MapLocator(MapView mv) {this.mv = mv;}

	public Location locate(MapFile file) {
	    Coord mc = new Coord(mv.getcc()).div(MCache.tilesz);
	    if(mc == null)
		throw(new Loading("Waiting for initial location"));
	    MCache.Grid plg = mv.ui.sess.glob.map.getgrid(mc.div(cmaps));
	    GridInfo info = file.gridinfo.get(plg.id);
	    if(info == null)
		throw(new Loading("No grid info, probably coming soon"));
	    Segment seg = file.segments.get(info.seg);
	    if(seg == null)
		throw(new Loading("No segment info, probably coming soon"));
	    return(new Location(seg, info.sc.mul(cmaps).add(mc.sub(plg.ul))));
	}
    }

    public void center(Location loc) {
	curloc = loc;
    }

    public void tick(double dt) {
	if(setloc != null) {
	    try {
		Location loc;
		if(!file.lock.readLock().tryLock())
		    throw(new Loading("Map file is busy"));
		try {
		    loc = setloc.locate(file);
		} finally {
		    file.lock.readLock().unlock();
		}
		center(loc);
		if(!follow)
		    setloc = null;
	    } catch(Loading l) {
	    }
	}
    }

    private void redisplay(Location loc) {
	Coord hsz = sz.div(2);
	Area next = Area.sized(loc.tc.sub(hsz).div(cmaps),
			       sz.add(cmaps).sub(1, 1).div(cmaps).add(1, 1));
	if((display == null) || (loc.seg != dseg) || !next.equals(dext)) {
	    DisplayGrid[] nd = new DisplayGrid[next.rsz()];
	    if((display != null) && (loc.seg == dseg)) {
		for(Coord c : dext) {
		    if(next.contains(c))
			nd[next.ri(c)] = display[dext.ri(c)];
		}
	    }
	    display = nd;
	    dseg = loc.seg;
	    dext = next;
	}
    }

    public static class DisplayGrid {
	public final Segment seg;
	public final Coord sc;
	public final Indir<Grid> gref;
	private Grid cgrid = null;
	private Defer.Future<Tex> img = null;

	public DisplayGrid(Segment seg, Coord sc, Indir<Grid> gref) {
	    this.seg = seg;
	    this.sc = sc;
	    this.gref = gref;
	}

	public Tex img() {
	    Grid grid = gref.get();
	    if(grid != cgrid) {
		if(img != null)
		    img.cancel();
		img = Defer.later(() -> new TexI(grid.render(sc.mul(cmaps))));
		cgrid = grid;
	    }
	    return((img == null)?null:img.get());
	}
    }

    public void draw(GOut g) {
	Location loc = this.curloc;
	if(loc == null)
	    return;
	Coord hsz = sz.div(2);
	redisplay(loc);
	if(file.lock.readLock().tryLock()) {
	    try {
		for(Coord c : dext) {
		    if(display[dext.ri(c)] == null)
			display[dext.ri(c)] = new DisplayGrid(loc.seg, c, loc.seg.grid(c));
		}
	    } finally {
		file.lock.readLock().unlock();
	    }
	}
	for(Coord c : dext) {
	    Tex img;
	    try {
		DisplayGrid disp = display[dext.ri(c)];
		if((disp == null) || ((img = disp.img()) == null))
		    continue;
	    } catch(Loading l) {
		continue;
	    }
	    Coord ul = hsz.add(c.mul(cmaps)).sub(loc.tc);
	    g.image(img, ul);
	}
    }

    public void center(Locator loc) {
	setloc = loc;
	follow = false;
    }

    public void follow(Locator loc) {
	setloc = loc;
	follow = true;
    }

    public boolean mousedown(Coord c, int button) {
	if(button == 1) {
	    Location loc = curloc;
	    if((drag == null) && (loc != null)) {
		drag = ui.grabmouse(this);
		dsc = c;
		dmc = loc.tc;
		dragging = false;
	    }
	    return(true);
	}
	return(super.mousedown(c, button));
    }

    public void mousemove(Coord c) {
	if(drag != null) {
	    if(dragging) {
		setloc = null;
		follow = false;
		curloc = new Location(curloc.seg, dmc.add(dsc.sub(c)));
	    } else if(c.dist(dsc) > 5) {
		dragging = true;
	    }
	}
	super.mousemove(c);
    }

    public boolean mouseup(Coord c, int button) {
	if((drag != null) && (button == 1)) {
	    drag.remove();
	    drag = null;
	}
	return(super.mouseup(c, button));
    }

    public static class MapWindow extends Window {
	public final MapFileWidget view;

	public MapWindow(MapFile file, Coord sz) {
	    super(sz, "Map");
	    view = add(new MapFileWidget(file, sz));
	    pack();
	}

	public MapWindow(MapFile file) {
	    this(file, new Coord(500, 500));
	}

	public boolean keydown(KeyEvent ev) {
	    if(ev.getKeyCode() == KeyEvent.VK_HOME) {
		view.follow(new MapFileWidget.MapLocator(getparent(GameUI.class).map));
		return(true);
	    }
	    return(super.keydown(ev));
	}
    }
}