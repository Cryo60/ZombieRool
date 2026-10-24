import me.cryo.zombierool.util.BloodCoverage;

public final class BloodCoverageTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(BloodCoverage.depositedAlpha(0,255)==0,"Exterior must remain transparent");
        check(BloodCoverage.depositedAlpha(66,255)==255,"Actual sprite core must be opaque");
        int edge=BloodCoverage.depositedAlpha(11,255);
        check(edge>0&&edge<255,"Edges must remain feathered");
        int tint=0x120c55,deposit=(edge<<24)|tint,pixel=0;
        for(int i=0;i<20;i++){
            int next=BloodCoverage.over(pixel,deposit);
            check((next>>>24)>=(pixel>>>24),"New deposits must increase coverage");
            check((next&0xffffff)==tint,"Accumulation must not multiply blood into black");
            pixel=next;
        }
        check((pixel>>>24)>240,"Repeated deposits must produce dense coverage");
        check(BloodCoverage.over(pixel,0)==pixel,"Empty pixels must not erase previous blood");
        check(BloodCoverage.over(pixel,0xff100850)==0xff100850,"Opaque deposit covers the old tint");
        System.out.println("PASS: opaque core, soft edges, progressive accumulation, stable tint and transparent exterior");
    }
}
