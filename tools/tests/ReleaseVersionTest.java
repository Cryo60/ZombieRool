import me.cryo.zombierool.util.ReleaseVersion;
public class ReleaseVersionTest {
 static int checks;
 static void test(String a,String b,boolean expected){if(ReleaseVersion.newer(a,b)!=expected)throw new AssertionError(a+" vs "+b);checks++;}
 public static void main(String[] args){
 test("ZombieRool-1.20.1-1.6.6-hotfix1","1.6.6-hotfix1",false);
 test("v26.7","26.7",false);test("26.7.0","26.7",false);
 test("26.7.1","26.7",true);test("26.8","26.7",true);
 test("1.6.6-hotfix2","1.6.6-hotfix1",true);
 test("1.6.6-hotfix1","1.6.6",true);
 test("26.7","26.7-dev",true);test("26.7-dev","26.7",false);
 test("1.6.6-hotfix1","26.7-dev",false);test("invalid","26.7",false);
 System.out.println("RELEASE VERSION PASS: "+checks+" cases");
 }
}
