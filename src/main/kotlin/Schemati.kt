package schemati

import co.aikar.commands.BaseCommand
import co.aikar.commands.CommandIssuer
import co.aikar.commands.PaperCommandManager
import co.aikar.commands.RegisteredCommand
import com.sk89q.worldedit.bukkit.WorldEditPlugin
import io.ktor.server.engine.ApplicationEngine
import org.bukkit.plugin.java.JavaPlugin
import schemati.connector.Database
import schemati.connector.NetworkDatabase
import schemati.web.AuthConfig
import schemati.web.startWeb
import java.io.File
import java.util.logging.Level
import kotlin.concurrent.thread

class Schemati : JavaPlugin() {
    private var web: ApplicationEngine? = null
    private var networkDatabase: Database? = null

    private fun handleCommandException(
        command: BaseCommand,
        registeredCommand: RegisteredCommand<*>,
        sender: CommandIssuer,
        args: List<String>,
        throwable: Throwable
    ): Boolean {
        logger.log(Level.SEVERE, "Error while executing command", throwable)
        val exception = throwable as? SchematicsException ?: return false
        // TODO: figure out what to do with sendError
        val message = exception.message ?: "Something went wrong!"
        sender.sendMessage("[Schemati] $message")
        return true
    }

    override fun onEnable() {
        loadConfig()

        val wePlugin = server.pluginManager.getPlugin("WorldEdit") as? WorldEditPlugin ?: throw Exception("no u")
        val schems = Schematics(File(config.getString("schematicsDirectory")!!))
        PaperCommandManager(this).apply {
            commandContexts.registerIssuerOnlyContext(PlayerSchematics::class.java) { context ->
                schems.forPlayer(context.player.uniqueId)
            }
            commandCompletions.registerCompletion("schematics", SchematicCompletionHandler(schems))
            registerCommand(Commands(wePlugin.worldEdit))
            setDefaultExceptionHandler(::handleCommandException, false)
        }

        networkDatabase = config.getConfigurationSection("network_database")!!.run {
            NetworkDatabase(
                database = getString("database")!!,
                username = getString("username")!!,
                password = getString("password")!!
            )
        }

        val oauthSection = config
            .getConfigurationSection("web")!!
            .getConfigurationSection("oauth")!!

        val authConfig = oauthSection.run {
            AuthConfig(
                clientId = getString("clientId")!!,
                clientSecret = getString("clientSecret")!!,
                scopes = getStringList("scopes")
            )
        }

        if (config.contains("web.port")) {
            web = startWeb(
                config.getConfigurationSection("web")!!.getInt("port"),
                networkDatabase!!,
                authConfig,
                schems
            )
        }
    }

    override fun onDisable() {
        networkDatabase?.unload()
        web?.stop(1000, 1000)
    }

    private fun loadConfig() {
        if (!dataFolder.exists()) {
            dataFolder.mkdir()
        }

        val file = File(dataFolder, "config.yml")

        if (!file.exists()) {
            file.createNewFile()
        }

        config.options().copyDefaults(true)
        saveConfig()
    }
}
