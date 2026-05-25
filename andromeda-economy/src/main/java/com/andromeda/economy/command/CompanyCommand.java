package com.andromeda.economy.command;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.CompanyData;
import com.andromeda.economy.data.CompanyManager;
import com.andromeda.economy.data.PlayerData;
import com.andromeda.economy.gui.CompanyGui;
import com.andromeda.economy.gui.CompanyMemberGui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CompanyCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("company")
            .executes(ctx -> open(ctx.getSource()))
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("apply")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> apply(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("accept")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, b) -> {
                        String prefix = b.getRemaining().toLowerCase(Locale.ROOT);
                        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
                            if (p.getName().getString().toLowerCase(Locale.ROOT).startsWith(prefix))
                                b.suggest(p.getName().getString());
                        });
                        return b.buildFuture();
                    })
                    .executes(ctx -> accept(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("reject")
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> reject(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("kick")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, b) -> {
                        String prefix = b.getRemaining().toLowerCase(Locale.ROOT);
                        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
                            if (p.getName().getString().toLowerCase(Locale.ROOT).startsWith(prefix))
                                b.suggest(p.getName().getString());
                        });
                        return b.buildFuture();
                    })
                    .executes(ctx -> kick(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("setshare")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, b) -> {
                        String prefix = b.getRemaining().toLowerCase(Locale.ROOT);
                        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
                            if (p.getName().getString().toLowerCase(Locale.ROOT).startsWith(prefix))
                                b.suggest(p.getName().getString());
                        });
                        return b.buildFuture();
                    })
                    .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                        .executes(ctx -> setShare(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            IntegerArgumentType.getInteger(ctx, "percent"))))))
            .then(Commands.literal("leave")
                .executes(ctx -> leave(ctx.getSource())))
            .then(Commands.literal("transfer")
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> transfer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
            .then(Commands.literal("disband")
                .executes(ctx -> disband(ctx.getSource())))
            .then(Commands.literal("top")
                .executes(ctx -> top(ctx.getSource())))
            .then(Commands.literal("info")
                .executes(ctx -> info(ctx.getSource())))
        );
    }

    // ── Subcommands ───────────────────────────────────────────────────────────

    private static int open(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        CompanyGui.open(player);
        return 1;
    }

    private static int create(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        CompanyManager mgr = AndromedaEconomy.company;
        String uuid = player.getStringUUID();

        if (name.length() > CompanyManager.MAX_NAME_LENGTH) {
            player.sendSystemMessage(Component.literal(
                "Name must be " + CompanyManager.MAX_NAME_LENGTH + " characters or fewer."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            player.sendSystemMessage(Component.literal(
                "Name may only contain letters, numbers, and underscores."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (mgr.getByMember(uuid) != null) {
            player.sendSystemMessage(Component.literal(
                "Leave your current company before creating one."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (mgr.nameExists(name)) {
            player.sendSystemMessage(Component.literal(
                "A company named '" + name + "' already exists."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        mgr.create(name, uuid);
        AndromedaEconomy.hud.update(player);
        player.sendSystemMessage(
            Component.literal("Company ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(name).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" created!").withStyle(ChatFormatting.GREEN))
        );
        return 1;
    }

    private static int apply(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        CompanyManager mgr = AndromedaEconomy.company;
        String uuid = player.getStringUUID();

        if (mgr.getByMember(uuid) != null) {
            player.sendSystemMessage(Component.literal(
                "Leave your current company before applying to another."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        CompanyData company = mgr.getByName(name);
        if (company == null) {
            player.sendSystemMessage(Component.literal("Company '" + name + "' not found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (company.pendingApplications.contains(uuid)) {
            player.sendSystemMessage(Component.literal(
                "You already have a pending application to this company."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        company.pendingApplications.add(uuid);
        mgr.save();
        player.sendSystemMessage(
            Component.literal("Application sent to ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(".").withStyle(ChatFormatting.WHITE))
        );
        // Notify the owner if online
        ServerPlayer owner = source.getServer().getPlayerList()
            .getPlayer(UUID.fromString(company.ownerUUID));
        if (owner != null) {
            owner.sendSystemMessage(
                Component.literal("[" + company.name + "] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" applied. Use /company accept " + player.getName().getString())
                        .withStyle(ChatFormatting.YELLOW))
            );
        }
        return 1;
    }

    private static int accept(CommandSourceStack source, String playerName) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) return 0;
        CompanyManager mgr = AndromedaEconomy.company;
        CompanyData company = mgr.getByMember(owner.getStringUUID());
        if (company == null || !company.isOwner(owner.getStringUUID())) {
            owner.sendSystemMessage(Component.literal("You don't own a company.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String applicantUUID = findUUIDInPending(company, playerName, source);
        if (applicantUUID == null) {
            owner.sendSystemMessage(Component.literal(
                "No pending application from '" + playerName + "'."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (mgr.getByMember(applicantUUID) != null) {
            company.pendingApplications.remove(applicantUUID);
            mgr.save();
            owner.sendSystemMessage(Component.literal(
                playerName + " is already in another company. Application removed."
            ).withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        company.pendingApplications.remove(applicantUUID);
        mgr.addMember(company, applicantUUID, 0);
        owner.sendSystemMessage(
            Component.literal(playerName + " accepted into ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
        );
        ServerPlayer applicant = source.getServer().getPlayerList().getPlayer(UUID.fromString(applicantUUID));
        if (applicant != null) {
            applicant.sendSystemMessage(
                Component.literal("You have been accepted into ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
            );
            AndromedaEconomy.hud.update(applicant);
        }
        return 1;
    }

    private static int reject(CommandSourceStack source, String playerName) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) return 0;
        CompanyManager mgr = AndromedaEconomy.company;
        CompanyData company = mgr.getByMember(owner.getStringUUID());
        if (company == null || !company.isOwner(owner.getStringUUID())) {
            owner.sendSystemMessage(Component.literal("You don't own a company.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String applicantUUID = findUUIDInPending(company, playerName, source);
        if (applicantUUID == null) {
            owner.sendSystemMessage(Component.literal(
                "No pending application from '" + playerName + "'."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        company.pendingApplications.remove(applicantUUID);
        mgr.save();
        owner.sendSystemMessage(Component.literal(
            "Application from " + playerName + " rejected."
        ).withStyle(ChatFormatting.YELLOW));
        ServerPlayer applicant = source.getServer().getPlayerList().getPlayer(UUID.fromString(applicantUUID));
        if (applicant != null) {
            applicant.sendSystemMessage(
                Component.literal("Your application to ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" was rejected.").withStyle(ChatFormatting.RED))
            );
        }
        return 1;
    }

    private static int kick(CommandSourceStack source, String playerName) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) return 0;
        CompanyManager mgr = AndromedaEconomy.company;
        CompanyData company = mgr.getByMember(owner.getStringUUID());
        if (company == null || !company.isOwner(owner.getStringUUID())) {
            owner.sendSystemMessage(Component.literal("You don't own a company.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String targetUUID = findUUIDByName(playerName, source);
        if (targetUUID == null || !company.memberShares.containsKey(targetUUID)) {
            owner.sendSystemMessage(Component.literal(
                "'" + playerName + "' is not a member of your company."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        mgr.removeMember(company, targetUUID);
        owner.sendSystemMessage(Component.literal(
            playerName + " has been kicked from " + company.name + "."
        ).withStyle(ChatFormatting.YELLOW));
        ServerPlayer target = source.getServer().getPlayerList().getPlayer(UUID.fromString(targetUUID));
        if (target != null) {
            target.sendSystemMessage(
                Component.literal("You have been kicked from ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
            );
            AndromedaEconomy.hud.update(target);
        }
        return 1;
    }

    private static int setShare(CommandSourceStack source, String playerName, int percent) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) return 0;
        CompanyManager mgr = AndromedaEconomy.company;
        CompanyData company = mgr.getByMember(owner.getStringUUID());
        if (company == null || !company.isOwner(owner.getStringUUID())) {
            owner.sendSystemMessage(Component.literal("You don't own a company.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String targetUUID = findUUIDByName(playerName, source);
        if (targetUUID == null || !company.memberShares.containsKey(targetUUID)) {
            owner.sendSystemMessage(Component.literal(
                "'" + playerName + "' is not a member of your company."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        int oldShare    = company.memberShares.getOrDefault(targetUUID, 0);
        int othersTotal = company.memberShares.values().stream().mapToInt(Integer::intValue).sum() - oldShare;
        if (othersTotal + percent > 100) {
            owner.sendSystemMessage(Component.literal(
                "Total member shares would exceed 100%. Max for this member: " + (100 - othersTotal) + "%"
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        mgr.setShare(company, targetUUID, percent);
        owner.sendSystemMessage(
            Component.literal(playerName + "'s share set to ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(percent + "%").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" — you keep ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(company.ownerShare() + "%").withStyle(ChatFormatting.GOLD))
        );
        return 1;
    }

    private static int leave(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        CompanyManager mgr = AndromedaEconomy.company;
        CompanyData company = mgr.getByMember(player.getStringUUID());
        if (company == null) {
            player.sendSystemMessage(Component.literal("You are not in a company.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (company.isOwner(player.getStringUUID())) {
            player.sendSystemMessage(Component.literal(
                "You own this company. Transfer ownership first or use /company disband."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        mgr.removeMember(company, player.getStringUUID());
        player.sendSystemMessage(
            Component.literal("You left ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
        );
        AndromedaEconomy.hud.update(player);
        return 1;
    }

    private static int transfer(CommandSourceStack source, String playerName) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) return 0;
        CompanyManager mgr = AndromedaEconomy.company;
        CompanyData company = mgr.getByMember(owner.getStringUUID());
        if (company == null || !company.isOwner(owner.getStringUUID())) {
            owner.sendSystemMessage(Component.literal("You don't own a company.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String targetUUID = findUUIDByName(playerName, source);
        if (targetUUID == null || !company.memberShares.containsKey(targetUUID)) {
            owner.sendSystemMessage(Component.literal(
                "'" + playerName + "' is not a member of your company."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }
        mgr.transferOwnership(company, targetUUID);
        owner.sendSystemMessage(
            Component.literal("Ownership of ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" transferred to " + playerName + ".").withStyle(ChatFormatting.WHITE))
        );
        AndromedaEconomy.hud.update(owner);
        ServerPlayer newOwner = source.getServer().getPlayerList().getPlayer(UUID.fromString(targetUUID));
        if (newOwner != null) {
            newOwner.sendSystemMessage(
                Component.literal("You are now the owner of ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
            );
            AndromedaEconomy.hud.update(newOwner);
        }
        return 1;
    }

    private static int disband(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) return 0;
        CompanyManager mgr = AndromedaEconomy.company;
        CompanyData company = mgr.getByMember(owner.getStringUUID());
        if (company == null || !company.isOwner(owner.getStringUUID())) {
            owner.sendSystemMessage(Component.literal("You don't own a company.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String name = company.name;
        MinecraftServer server = source.getServer();
        // Notify all members before clearing data
        for (String memberUUID : CompanyManager.allMemberUUIDs(company)) {
            if (memberUUID.equals(owner.getStringUUID())) continue;
            ServerPlayer member = server.getPlayerList().getPlayer(UUID.fromString(memberUUID));
            if (member != null) {
                member.sendSystemMessage(Component.literal(
                    "Company " + name + " has been disbanded."
                ).withStyle(ChatFormatting.RED));
            }
        }
        mgr.disband(name);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            AndromedaEconomy.hud.update(p);
        }
        owner.sendSystemMessage(
            Component.literal("Company ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(name).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" disbanded.").withStyle(ChatFormatting.RED))
        );
        return 1;
    }

    private static int top(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        List<CompanyData> list = AndromedaEconomy.company.allSortedByRevenue();
        player.sendSystemMessage(Component.literal("--- Top Companies ---").withStyle(ChatFormatting.GOLD));
        int limit = Math.min(list.size(), 10);
        for (int i = 0; i < limit; i++) {
            CompanyData c = list.get(i);
            int rank = i + 1;
            player.sendSystemMessage(
                Component.literal("#" + rank + " ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(c.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(EconomyUtils.compact(c.revenue) + " THB").withStyle(ChatFormatting.GREEN))
            );
        }
        if (list.isEmpty())
            player.sendSystemMessage(Component.literal("No companies yet.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int info(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        CompanyData company = AndromedaEconomy.company.getByMember(player.getStringUUID());
        if (company == null) {
            player.sendSystemMessage(Component.literal("You are not in a company.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        player.sendSystemMessage(Component.literal("--- " + company.name + " ---").withStyle(ChatFormatting.GOLD));
        PlayerData ownerData = AndromedaEconomy.db.getPlayer(company.ownerUUID);
        String ownerName = ownerData != null ? ownerData.username : company.ownerUUID;
        player.sendSystemMessage(
            Component.literal("Owner: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(ownerName).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" (" + company.ownerShare() + "%)").withStyle(ChatFormatting.GRAY))
        );
        for (var entry : company.memberShares.entrySet()) {
            PlayerData md = AndromedaEconomy.db.getPlayer(entry.getKey());
            String mName = md != null ? md.username : entry.getKey();
            player.sendSystemMessage(
                Component.literal("  " + mName).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" (" + entry.getValue() + "%)").withStyle(ChatFormatting.GRAY))
            );
        }
        player.sendSystemMessage(
            Component.literal("Revenue: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(EconomyUtils.compact(company.revenue) + " THB").withStyle(ChatFormatting.GREEN))
        );
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String findUUIDInPending(CompanyData company, String playerName, CommandSourceStack source) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (online != null && company.pendingApplications.contains(online.getStringUUID()))
            return online.getStringUUID();
        PlayerData db = AndromedaEconomy.db.getPlayerByName(playerName);
        if (db != null && company.pendingApplications.contains(db.uuid))
            return db.uuid;
        return null;
    }

    private static String findUUIDByName(String playerName, CommandSourceStack source) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (online != null) return online.getStringUUID();
        PlayerData db = AndromedaEconomy.db.getPlayerByName(playerName);
        return db != null ? db.uuid : null;
    }
}
