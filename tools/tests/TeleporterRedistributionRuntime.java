package local.hotfixtest;

@net.minecraftforge.fml.common.Mod("hotfixtests")
public final class TeleporterRedistributionRuntime {
    public TeleporterRedistributionRuntime(){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::started);}
    private void started(net.minecraftforge.event.server.ServerStartedEvent event){
        try{TeleporterPlayerLockTest.run(event.getServer().overworld());TeleporterCooldownTest.run(event.getServer().overworld());TeleporterRedistributionTest.run(event.getServer().overworld());}
        catch(Throwable failure){failure.printStackTrace();}
        finally{event.getServer().halt(false);}
    }
}
