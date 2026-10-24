package me.cryo.zombierool.block;

public enum SignalChannel {
    ALPHA("alpha", "Alpha"),
    BETA("beta", "Beta"),
    OMEGA("omega", "Omega"),
    ULTIMA("ultima", "Ultima"),
    GAMMA("gamma", "Gamma"),
    DELTA("delta", "Delta"),
    EPSILON("epsilon", "Epsilon"),
    ZETA("zeta", "Zeta"),
    ETA("eta", "Eta"),
    THETA("theta", "Theta"),
    IOTA("iota", "Iota"),
    KAPPA("kappa", "Kappa"),
    LAMBDA("lambda", "Lambda"),
    SIGMA("sigma", "Sigma");

    private final String id;
    private final String displayName;

    SignalChannel(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}
