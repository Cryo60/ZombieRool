import me.cryo.zombierool.client.TacZCamoParts;
import static me.cryo.zombierool.client.TacZCamoParts.Finish.*;

public final class TacZCamoPartsTest {
    private static void expect(String name,boolean light,TacZCamoParts.Finish parent,TacZCamoParts.Finish result){
        if(TacZCamoParts.resolve(name,light,parent)!=result)throw new AssertionError(name+" under "+parent+" should be "+result);
    }
    public static void main(String[] args){
        for(String name:new String[]{"barrel_front12","bolt_rotate","trigger2","body_rail6","fore_sight",
            "jimiao","Rsight2","mag_release","MagLatch","charginghandle","aug_charge","saftyswitch",
            "bullet_in_mag","shell2","suppressor","muzzle_default","gas_tube2","safety2"})expect(name,false,PAINT,METAL);
        for(String name:new String[]{"handguard_tactical","tactical_handgraud","stock_default","receiver_lower",
            "mag_standard","slide","upper","wood4","trigger_guard2"})expect(name,false,PAINT,PAINT);
        expect("handguard",false,METAL,PAINT);
        for(String name:new String[]{"handle","pistol_grip","foregrip","mag30","mag_box","drum_mag","magezine","extdmag9"})expect(name,false,METAL,PAINT);
        for(String name:new String[]{"charging_handle","pullhandle","mag_release","bullet_in_magazine","R_bullet"})expect(name,false,PAINT,METAL);
        expect("bone12",false,METAL,METAL);
        expect("bone12",false,PAINT,PAINT);
        expect("mag_and_bullet",false,PAINT,PAINT);
        expect("gun_and_righthand",false,PAINT,PAINT);
        expect("righthand_pos",false,PAINT,PROTECTED);
        expect("righthand",false,PAINT,PAINT);
        expect("lefthand",false,PAINT,PAINT);
        expect("receiver",false,PROTECTED,PROTECTED);
        expect("scope_lens",false,PAINT,PROTECTED);
        expect("barrel_illuminated",false,PAINT,PROTECTED);
        expect("bone",true,PAINT,PROTECTED);
        System.out.println("PASS: gunpack aliases, protected hands/optics, compound groups and inherited finishes");
    }
}
