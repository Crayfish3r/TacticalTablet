package com.makar.tacticaltablet.tablet.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BaseClassLayoutTest {
    @Test void threeRowsFitInsideOriginalContentWithTabs() {
        assertEquals(3,BaseClassLayout.VISIBLE_ROWS);
        assertTrue(BaseClassLayout.TOP+2*BaseClassLayout.ROW_STEP+BaseClassLayout.ROW_HEIGHT<=BaseClassLayout.HEIGHT);
        assertTrue(2*BaseClassLayout.CARD_WIDTH+BaseClassLayout.GAP<=BaseClassLayout.WIDTH);
        assertEquals(1,BaseClassLayout.maximumOffset(7,false));
        assertEquals(4,BaseClassLayout.maximumOffset(7,true));
        assertEquals(4,ScrollableGridLayout.VISIBLE_ROWS);
        assertEquals(34,ScrollableGridLayout.CARD_HEIGHT);
    }
    @Test void arrowsRevealRowsAndNeverWrapAcrossColumns() {
        assertEquals(1,BaseClassLayout.move(1,7,false,1,0));
        assertEquals(3,BaseClassLayout.move(1,7,false,0,1));
        assertEquals(1,BaseClassLayout.reveal(6,0,7,false));
        assertEquals(4,BaseClassLayout.reveal(6,0,7,true));
        assertEquals(0,BaseClassLayout.reveal(0,4,7,true));
        assertEquals(0,BaseClassLayout.clamp(-10,7,true));
        assertEquals(4,BaseClassLayout.clamp(999,7,true));
    }
    @Test void detailNavigationKeepsClassScrollAndUnrelatedPages() {
        TabletPageState s=new TabletPageState();
        s.classMode(TabletPageState.ClassMode.PROGRESSION);
        s.scroll("classes.progress",2,4);
        s.openClass("sniper");
        s.closeClass();
        assertEquals("sniper",s.selectedClass());
        assertFalse(s.classDetails());
        assertEquals(2,s.offset("classes.progress"));
        assertEquals(0,s.offset("classes.select"));
        s.selectPage(1,5);
        s.selectPage(0,5);
        assertEquals(TabletPageState.ClassMode.PROGRESSION,s.classMode());
        assertEquals("sniper",s.selectedClass());
    }
    @Test void tabRevealsAllSevenRowsInBothDirections() {
        int offset=0;
        for(int index=0;index<7;index++) {
            offset=BaseClassLayout.reveal(index,offset,7,true);
            assertTrue(index>=offset && index<offset+BaseClassLayout.VISIBLE_ROWS);
            assertEquals(index<6 ? index+1 : -1,BaseClassLayout.tabIndex(index,7,false));
        }
        for(int index=6;index>=0;index--) {
            offset=BaseClassLayout.reveal(index,offset,7,true);
            assertTrue(index>=offset && index<offset+BaseClassLayout.VISIBLE_ROWS);
            assertEquals(index>0 ? index-1 : -1,BaseClassLayout.tabIndex(index,7,true));
        }
    }
}
