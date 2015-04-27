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

import java.awt.Color;
import java.awt.Font;
import java.util.*;

public class Polity extends Window {
    public final String name;
    public int auth, acap, adrain;
    public boolean offline;
    private final List<Member> memb = new ArrayList<Member>();
    private final Map<Integer, Member> idmap = new HashMap<Integer, Member>();
    private MemberList ml;
    private Widget mw;
    
    @RName("pol")
    public static class $_ implements Factory {
	public Widget create(Widget parent, Object[] args) {
	    return(new Polity((String)args[0]));
	}
    }
    
    public class Member {
	public final Integer id;
	
	private Member(Integer id) {
	    this.id = id;
	}
    }
    
    private class MemberList extends Listbox<Member> {
	final Text unk = Text.render("???");
	final Text self = Text.render("You", new Color(192, 192, 255));
	
	private MemberList(int w, int h) {
	    super(w, h, 20);
	}
	
	public Member listitem(int idx) {return(memb.get(idx));}
	public int listitems() {return(memb.size());}

	public void drawitem(GOut g, Member m, int idx) {
	    if((mw instanceof MemberWidget) && (((MemberWidget)mw).id == m.id))
		drawsel(g);
	    Text rn;
	    if(m.id == null) {
		rn = self;
	    } else {
		BuddyWnd.Buddy b = getparent(GameUI.class).buddies.find(m.id);
		rn = (b == null)?unk:(b.rname());
	    }
	    g.aimage(rn.tex(), new Coord(0, 10), 0, 0.5);
	}

	public void change(Member pm) {
	    if(pm == null)
		Polity.this.wdgmsg("sel");
	    else
		Polity.this.wdgmsg("sel", pm.id);
	}
    }
    
    public static abstract class MemberWidget extends Widget {
	public final Integer id;
	
	public MemberWidget(Coord sz, Integer id) {
	    super(sz);
	    this.id = id;
	}
    }

    public static final Text.Foundry nmf = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, 14)).aa(true);
    public static final Text.Foundry membf = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, 12)).aa(true);

    public Polity(String name) {
	super(new Coord(200, 200), "Village", true, Coord.z, Coord.z);
	this.name = name;
	add(new Label(name, nmf), new Coord(0, 5));
	add(new Label("Members:"), new Coord(0, 45));
	ml = add(new MemberList(200, 7), new Coord(0, 60));
	pack();
    }
    
    private Tex rauth = null;
    public void cdraw(GOut g) {
	if(acap > 0) {
	    synchronized(this) {
		g.chcolor(0, 0, 0, 255);
		g.frect(new Coord(0, 23), new Coord(200, 20));
		g.chcolor(128, 0, 0, 255);
		g.frect(new Coord(0, 24), new Coord((200 * auth) / acap, 18));
		g.chcolor();
		if(rauth == null) {
		    Color col = offline?Color.RED:Color.WHITE;
		    rauth = new TexI(Utils.outline2(Text.render(String.format("%s/%s", auth, acap), col).img, Utils.contrast(col)));
		}
		g.aimage(rauth, new Coord(100, 33), 0.5, 0.5);
	    }
	}
    }
    
    public void uimsg(String msg, Object... args) {
	if(msg == "auth") {
	    synchronized(this) {
		auth = (Integer)args[0];
		acap = (Integer)args[1];
		adrain = (Integer)args[2];
		offline = ((Integer)args[3]) != 0;
		rauth = null;
	    }
	} else if(msg == "add") {
	    Integer id = (Integer)args[0];
	    Member pm = new Member(id);
	    synchronized(this) {
		memb.add(pm);
		idmap.put(id, pm);
	    }
	} else if(msg == "rm") {
	    Integer id = (Integer)args[0];
	    synchronized(this) {
		Member pm = idmap.get(id);
		memb.remove(pm);
		idmap.remove(id);
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }
    
    public void addchild(Widget child, Object... args) {
	if(args[0] instanceof String) {
	    String p = (String)args[0];
	    if(p.equals("m")) {
		mw = child;
		add(child, 0, 210);
		pack();
		return;
	    }
	}
	super.addchild(child, args);
    }

    public void cdestroy(Widget w) {
	if(w == mw) {
	    mw = null;
	    pack();
	}
    }
}
