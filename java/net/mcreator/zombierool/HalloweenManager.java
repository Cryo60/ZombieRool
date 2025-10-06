package net.mcreator.zombierool;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Gestionnaire centralisé pour les événements d'Halloween.
 * Permet de détecter la période d'Halloween et d'appliquer des modifications
 * automatiques aux entités ajoutées au monde.
 */
@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HalloweenManager {

    // ==================== CONFIGURATION ====================
    
    /**
     * Jour de début de la période Halloween en octobre (inclus)
     */
    private static final int HALLOWEEN_START_DAY_OCTOBER = 20;
    
    /**
     * Jour de fin de la période Halloween en novembre (inclus)
     */
    private static final int HALLOWEEN_END_DAY_NOVEMBER = 5;
    
    /**
     * Force le mode Halloween indépendamment de la date
     * Modifier cette valeur à true pour activer Halloween en permanence
     */
    private static boolean FORCE_HALLOWEEN_MODE = false;
    
    // Couleurs possibles pour l'armure en cuir (format RGB)
    private static final int[] ARMOR_COLORS = {
        0xFF8C00,  // Orange foncé (couleur principale)
        0xFFA500,  // Orange
        0xFF4500,  // Orange rouge
        0x8B4513,  // Marron
        0x000000,  // Noir
        0x800080   // Violet
    };
    
    // ==================== REGISTRY ====================
    
    /**
     * Registry des modifications à appliquer par classe d'entité
     */
    private static final Map<Class<? extends Entity>, Consumer<Entity>> ENTITY_MODIFIERS = new HashMap<>();
    
    static {
        // Enregistrement des modifications pour chaque type d'entité
        registerEntityModifier(net.mcreator.zombierool.entity.ZombieEntity.class, entity -> {
            net.mcreator.zombierool.entity.ZombieEntity zombie = (net.mcreator.zombierool.entity.ZombieEntity) entity;
            Random random = new Random();
            
            // Ajout d'une Jack O'Lantern sur le zombie
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.JACK_O_LANTERN));
            
            // Choix d'une couleur pour l'armure (70% de chance d'avoir orange, 30% autre couleur)
            int armorColor;
            if (random.nextFloat() < 0.7F) {
                // Orange (première couleur du tableau)
                armorColor = ARMOR_COLORS[0];
            } else {
                // Autre couleur aléatoire
                armorColor = ARMOR_COLORS[random.nextInt(ARMOR_COLORS.length)];
            }
            
            // Équipement de l'armure en cuir teintée
            ItemStack chestplate = createDyedLeatherArmor(Items.LEATHER_CHESTPLATE, armorColor);
            ItemStack leggings = createDyedLeatherArmor(Items.LEATHER_LEGGINGS, armorColor);
            ItemStack boots = createDyedLeatherArmor(Items.LEATHER_BOOTS, armorColor);
            
            zombie.setItemSlot(EquipmentSlot.CHEST, chestplate);
            zombie.setItemSlot(EquipmentSlot.LEGS, leggings);
            zombie.setItemSlot(EquipmentSlot.FEET, boots);
            
            // 40% de chance d'avoir un outil dans la main
            if (random.nextFloat() < 0.4F) {
                ItemStack tool;
                if (random.nextBoolean()) {
                    tool = new ItemStack(Items.WOODEN_HOE);
                } else {
                    tool = new ItemStack(Items.WOODEN_SHOVEL);
                }
                zombie.setItemSlot(EquipmentSlot.MAINHAND, tool);
            }
            
            // Empêche le zombie de drop son équipement
            zombie.setDropChance(EquipmentSlot.HEAD, 0.0F);
            zombie.setDropChance(EquipmentSlot.CHEST, 0.0F);
            zombie.setDropChance(EquipmentSlot.LEGS, 0.0F);
            zombie.setDropChance(EquipmentSlot.FEET, 0.0F);
            zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        });
        
        // Modification pour le Crawler - change le skin pour Halloween
        registerEntityModifier(net.mcreator.zombierool.entity.CrawlerEntity.class, entity -> {
            net.mcreator.zombierool.entity.CrawlerEntity crawler = (net.mcreator.zombierool.entity.CrawlerEntity) entity;
            
            // Active le skin Halloween pour le crawler
            crawler.setHalloweenSkin(true);
        });
    }
    
    // ==================== MÉTHODES UTILITAIRES ====================
    
    /**
     * Crée une pièce d'armure en cuir teintée avec la couleur spécifiée
     * Compatible avec Minecraft 1.20.1
     * 
     * @param armorItem L'item d'armure en cuir
     * @param color La couleur au format RGB (0xRRGGBB)
     * @return L'ItemStack de l'armure teintée
     */
    private static ItemStack createDyedLeatherArmor(net.minecraft.world.item.Item armorItem, int color) {
        ItemStack stack = new ItemStack(armorItem);
        CompoundTag displayTag = stack.getOrCreateTagElement("display");
        displayTag.putInt("color", color);
        return stack;
    }
    
    // ==================== API PUBLIQUE ====================
    
    /**
     * Enregistre une modification à appliquer pour une classe d'entité spécifique
     * 
     * @param entityClass La classe de l'entité à modifier
     * @param modifier La fonction de modification à appliquer
     * @param <T> Le type d'entité
     */
    public static <T extends Entity> void registerEntityModifier(
            Class<T> entityClass, 
            Consumer<T> modifier) {
        ENTITY_MODIFIERS.put(entityClass, (Consumer<Entity>) modifier);
    }
    
    /**
     * Vérifie si nous sommes actuellement dans la période d'Halloween
     * 
     * @return true si c'est Halloween, false sinon
     */
    public static boolean isHalloweenPeriod() {
        if (FORCE_HALLOWEEN_MODE) {
            return true;
        }
        
        LocalDate today = LocalDate.now();
        Month currentMonth = today.getMonth();
        int currentDay = today.getDayOfMonth();
        
        // Vérification pour octobre (du HALLOWEEN_START_DAY_OCTOBER au 31)
        if (currentMonth == Month.OCTOBER && currentDay >= HALLOWEEN_START_DAY_OCTOBER) {
            return true;
        }
        
        // Vérification pour novembre (du 1er au HALLOWEEN_END_DAY_NOVEMBER)
        if (currentMonth == Month.NOVEMBER && currentDay <= HALLOWEEN_END_DAY_NOVEMBER) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Force ou désactive le mode Halloween manuellement
     * 
     * @param forced true pour forcer Halloween, false pour utiliser la date réelle
     */
    public static void setForceHalloweenMode(boolean forced) {
        FORCE_HALLOWEEN_MODE = forced;
    }
    
    /**
     * Vérifie si le mode Halloween est forcé
     * 
     * @return true si le mode est forcé, false sinon
     */
    public static boolean isHalloweenModeForced() {
        return FORCE_HALLOWEEN_MODE;
    }
    
    /**
     * Applique les modifications Halloween à une entité si applicable
     * 
     * @param entity L'entité à modifier
     */
    public static void applyHalloweenModifications(Entity entity) {
        if (!isHalloweenPeriod()) {
            return;
        }
        
        Class<? extends Entity> entityClass = entity.getClass();
        Consumer<Entity> modifier = ENTITY_MODIFIERS.get(entityClass);
        
        if (modifier != null) {
            modifier.accept(entity);
        }
    }
    
    // ==================== EVENT HANDLER ====================
    
    /**
     * Événement déclenché quand une entité rejoint le monde
     * Applique automatiquement les modifications Halloween si nécessaire
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();
        
        // Uniquement côté serveur
        if (level.isClientSide()) {
            return;
        }
        
        // Application des modifications Halloween
        applyHalloweenModifications(entity);
    }
    
    // ==================== UTILITAIRES ====================
    
    /**
     * Retourne une description de la configuration Halloween actuelle
     * Utile pour le debug
     * 
     * @return String décrivant l'état du système Halloween
     */
    public static String getHalloweenStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== Halloween Manager Status ===\n");
        status.append("Force Mode: ").append(FORCE_HALLOWEEN_MODE).append("\n");
        status.append("Current Period: ").append(isHalloweenPeriod() ? "ACTIVE" : "INACTIVE").append("\n");
        status.append("Date Range: October ").append(HALLOWEEN_START_DAY_OCTOBER)
              .append(" - November ").append(HALLOWEEN_END_DAY_NOVEMBER).append("\n");
        status.append("Registered Entities: ").append(ENTITY_MODIFIERS.size()).append("\n");
        status.append("Current Date: ").append(LocalDate.now()).append("\n");
        return status.toString();
    }
}