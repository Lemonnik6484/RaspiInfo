package dev.lemonnik.respinfo;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.sun.management.OperatingSystemMXBean;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;

@Mod(RaspiInfo.MODID)
public class RaspiInfo {
    public static final String MODID = "raspinfo";
    private static final Logger LOGGER = LogUtils.getLogger();

    public RaspiInfo(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Initializing Raspi Info!");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("sysinfo")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            String info = getSystemInfo();
                            source.sendSuccess(Component.literal(info), false);
                            return 1;
                        })
        );
    }

    private String getSystemInfo() {
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean)
                    ManagementFactory.getOperatingSystemMXBean();

            double cpuLoad = osBean.getSystemCpuLoad() * 100.0;
            long totalMem = osBean.getTotalPhysicalMemorySize();
            long freeMem = osBean.getFreePhysicalMemorySize();
            long usedMem = totalMem - freeMem;

            double usedMB = usedMem / 1024.0 / 1024.0;
            double totalMB = totalMem / 1024.0 / 1024.0;
            double cpuTemp = getPiTemp();

            DiskInfo disk = getDiskInfo();

            String tempDisplay = cpuTemp < 0 ? "Unavailable" : String.format("%.1f°C", cpuTemp);

            return String.format(
                    "§6[SYSINFO]§r CPU: §a%.1f%%§r | Temp: §c%s§r | RAM: §b%.0f / %.0f MB§r | Disk: §e%.1f / %.1f GB (%.0f%% free)§r",
                    cpuLoad, tempDisplay, usedMB, totalMB,
                    disk.usedGB, disk.totalGB, disk.freePercent
            );
        } catch (Exception e) {
            LOGGER.error("Failed to get system info", e);
            return "§c[SYSINFO] Failed to get system info: " + e.getMessage();
        }
    }

    private double getPiTemp() {
        try {
            Process proc = Runtime.getRuntime().exec("vcgencmd measure_temp");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String output = reader.readLine();
                if (output != null && output.contains("=")) {
                    String value = output.split("=")[1].replace("'", "").replace("C", "").replace("°", "").trim();
                    return Double.parseDouble(value);
                }
            }
        } catch (IOException ignored) {}

        try {
            String tempStr = Files.readString(Path.of("/sys/class/thermal/thermal_zone0/temp")).trim();
            return Double.parseDouble(tempStr) / 1000.0;
        } catch (IOException e) {
            LOGGER.warn("Temperature read failed: {}", e.getMessage());
            return -1;
        }
    }

    private DiskInfo getDiskInfo() {
        try {
            File current = new File(".");
            long total = current.getTotalSpace();
            long free = current.getFreeSpace();
            long used = total - free;

            double totalGB = total / 1024.0 / 1024.0 / 1024.0;
            double usedGB = used / 1024.0 / 1024.0 / 1024.0;
            double freePercent = (free * 100.0) / total;

            return new DiskInfo(usedGB, totalGB, freePercent);
        } catch (Exception e) {
            LOGGER.warn("Disk info read failed: {}", e.getMessage());
            return new DiskInfo(0, 0, 0);
        }
    }

    private record DiskInfo(double usedGB, double totalGB, double freePercent) {
    }
}