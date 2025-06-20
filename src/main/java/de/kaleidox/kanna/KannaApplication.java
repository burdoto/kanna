package de.kaleidox.kanna;

import de.kaleidox.kanna.util.ApplicationContextProvider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.comroid.annotations.Description;
import org.comroid.api.config.ConfigurationManager;
import org.comroid.api.func.ext.Context;
import org.comroid.api.func.util.Command;
import org.comroid.api.io.FileFlag;
import org.comroid.api.io.FileHandle;
import org.jetbrains.annotations.Nullable;
import org.mariadb.jdbc.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.awt.*;
import java.io.File;
import java.util.Locale;

@Slf4j
@SpringBootApplication
@ComponentScan(basePackageClasses = ApplicationContextProvider.class)
public class KannaApplication {
    public static final long KANNAS_CAVE_GUILD_ID = 1385561216713494660L;
    public static final File COMMAND_PURGE_FILE   = new File("./purge_commands");
    public static final Color THEME              = new Color(0xe69cd9);
    public static final Color WARNING            = new Color(0x493381);

    public static void main(String[] args) {
        SpringApplication.run(KannaApplication.class, args);
    }

    @Command(permission = "8")
    @Description("Shutdown the Bot")
    public static String shutdown(
            User user, @Command.Arg(value = "purgecommands", required = false) @Description(
                    "Purge and recreate all bot commands on next startup") @Nullable Boolean purgeCommands
    ) {
        if (Boolean.TRUE.equals(purgeCommands)) FileFlag.enable(COMMAND_PURGE_FILE);
        System.exit(0);
        return "Goodbye";
    }

    @Bean
    public Locale locale() {
        var locale = Locale.GERMAN;
        Locale.setDefault(locale);
        return locale;
    }

    @Bean
    public FileHandle configDir() {
        return new FileHandle("/srv/discord/kanna", true);
    }

    @Bean
    public FileHandle configFile(@Autowired FileHandle configDir) {
        return configDir.createSubFile("config.json");
    }

    @Bean
    public ConfigurationManager<BotConfig> configManager(@Autowired Context context, @Autowired FileHandle configFile) {
        return new ConfigurationManager<>(context, BotConfig.class, configFile.getAbsolutePath());
    }

    @Bean
    @Order
    public BotConfig config(@Autowired ConfigurationManager<BotConfig> configManager) {
        configManager.initialize();
        return configManager.getConfig();
    }

    @Bean
    public DataSource database(@Autowired BotConfig config) {
        return DataSourceBuilder.create()
                .driverClassName(Driver.class.getCanonicalName())
                .url(config.database.uri)
                .username(config.database.username)
                .password(config.database.password)
                .build();
    }

    @Bean
    public JDA jda(@Autowired BotConfig config) {
        return JDABuilder.create(config.token, GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS)).build();
    }

    @Bean
    public EmojiManagementController emojiManagementController(@Autowired BotConfig config, @Autowired JDA jda) {
        return new EmojiManagementController(config, jda);
    }

    @Bean
    public Command.Manager cmdr() {
        var cmdr = new Command.Manager();
        cmdr.addChild(this);

        cmdr.register(this);

        return cmdr;
    }

    @Bean
    public Command.Manager.Adapter$JDA cmdrJdaAdapter(@Autowired Command.Manager cmdr, @Autowired JDA jda)
    throws InterruptedException {
        try {
            var adp = cmdr.new Adapter$JDA(jda.awaitReady());
            adp.setPurgeCommands(FileFlag.consume(COMMAND_PURGE_FILE));
            return adp;
        } finally {
            cmdr.initialize();
        }
    }
}
