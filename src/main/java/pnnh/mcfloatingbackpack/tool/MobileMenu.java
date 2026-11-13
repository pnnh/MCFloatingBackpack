package pnnh.mcfloatingbackpack.tool;

/** 054: a menu that a mobile tool opened (the server tells it from any other menu by this, to close it when its tool is clicked again). */
public interface MobileMenu {
    MobileTool tool();
}
