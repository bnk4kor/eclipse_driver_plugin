package agent;

import com.bosch.eclipsedriver.agent.core.Paging;
import edtest.Assert;
import java.util.List;

public class PagingTest {
    public void testSliceRangeWithinBounds() {
        Paging.Window w = Paging.window(10, null, null, 5, 20);
        Assert.eq(0, w.offset());
        Assert.eq(5, w.limit());
        Assert.eq(List.of("a", "b", "c"), Paging.slice(List.of("a", "b", "c", "d", "e"), 0, 3));
    }

    public void testSliceClampsOffsetAndLimit() {
        Paging.Window w = Paging.window(5, -3, 100, 10, 20);
        Assert.eq(0, w.offset());
        Assert.eq(20, w.limit());
        Assert.eq(List.of("d", "e"), Paging.slice(List.of("a", "b", "c", "d", "e"), 3, 10));
        Assert.eq(List.of(), Paging.slice(List.of("a", "b"), 5, 3));
    }

    public void testLinesWindowAndHasMore() {
        Paging.TextSlice s = Paging.sliceLines("line1\nline2\r\nline3\nline4", 1, 2, 10, 50);
        Assert.eq(4, s.totalLines());
        Assert.eq(1, s.offset());
        Assert.eq(2, s.limit());
        Assert.eq("line2\nline3", s.text());
        Assert.eq(List.of("line2", "line3"), s.lines());
        Assert.isTrue(s.hasMore(), "hasMore when offset+returned < totalLines");

        Paging.TextSlice tail = Paging.sliceLines("line1\nline2\nline3", 1, 5, 10, 50);
        Assert.eq(List.of("line2", "line3"), tail.lines());
        Assert.isTrue(!tail.hasMore(), "no more at tail");
    }

    public void testEmptyStringLines() {
        Paging.TextSlice s = Paging.sliceLines("", 0, 10, 10, 50);
        Assert.eq(0, s.totalLines());
        Assert.eq("", s.text());
        Assert.eq(List.of(), s.lines());
        Assert.isTrue(!s.hasMore(), "empty has no more");
    }
}
