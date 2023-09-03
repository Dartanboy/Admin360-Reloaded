package com.battleasya.admin360.handler;

import com.battleasya.admin360.Admin360;
import com.battleasya.admin360.entities.Admin;
import com.battleasya.admin360.entities.Request;
import com.battleasya.admin360.entities.Review;
import com.battleasya.admin360.entities.User;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class RequestHandler {

    private final Admin360 plugin;

    /** RequestHandler Constructor */
    public RequestHandler(Admin360 plugin) {
        this.plugin = plugin;
    }

    /** Adds Request to Queue */
    public void createTicket(CommandSender sender, String comment) {

        String playerName = sender.getName();
        UUID playerID = ((Player) sender).getUniqueId();

        // check staff availability
        if (Config.check_staff_availability && !Admin.isAvailable()) {
            User.messagePlayer(sender, Config.create_failed_no_staff);
            return;
        }

        // check user status
        switch (Request.getStatus(playerID)) {
            case 1:
                User.messagePlayer(sender, Config.create_failed_pending);
                return;
            case 2:
                User.messagePlayer(sender, Config.create_failed_attending);
                return;
            case 3:
                User.messagePlayer(sender, Config.create_failed_completing);
                return;
        }

        // check cooldown status
        if (Config.create_cooldown_enable){
            long secondsLeft = User.inCooldown(playerID, Config.create_cooldown_interval, plugin);
            if (secondsLeft != -1) { // still in cooldown
                String secondsLeft2S = String.valueOf(secondsLeft);
                User.messagePlayer(sender, Config.create_cooldown_message
                        .replaceAll("<SECONDSLEFT>", secondsLeft2S));
                return;
            }
        }

        // create request
        Request request = new Request(playerID, playerName, comment);
        Request.addToPndLst(request); // add to queue

        String positionInPending = Integer.toString(Request.getPndLstSize());

        // notify user
        for (String message : Config.create_passed_notify_player) {
            User.messagePlayer(sender, message
                    .replaceAll("<POSITION>", positionInPending));
        }

        // notify staff
        for (String message : Config.create_passed_notify_staff) {
            Admin.messageAdmins(message
                    .replaceAll("<PLAYERNAME>", playerName)
                    .replaceAll("<DETAILS>", comment)
                    .replaceAll("<TICKETSREMAIN>", positionInPending));
        }

        // trigger custom commands
        if (Config.create_passed_trigger_enable) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Config.create_passed_trigger_command
                    .replaceAll("<PLAYERNAME>", playerName)
                    .replaceAll("<POSITION>", positionInPending));
        }

    }


    public void printPendingList(CommandSender admin) {

        int i = 1;

        // loop through each Request in Pending List
        for (Request request : Request.requestPending) {

            String index = Integer.toString(i);
            String playerName = request.getPlayerName();
            String comment = request.getComment();
            String datetime = new SimpleDateFormat("MM/dd/yy HH:mm")
                    .format(new Date(request.getTimestamp() * 1000));

            User.messagePlayer(admin, Config.list_message
                    .replaceAll("<INDEX>", index)
                    .replaceAll("<PLAYERNAME>", playerName)
                    .replaceAll("<DETAILS>", comment)
                    .replaceAll("<DATETIME>", datetime));

            i++;

        }

    }


    public void attendTicket(CommandSender admin, String playerName) {

        String adminName = admin.getName();
        UUID adminID = ((Player) admin).getUniqueId();

        Player player;

        Request request;

        // this must go first to avoid problems
        if (Request.inAtdLst(adminID)) {
            User.messagePlayer(admin, Config.attend_failed_attending);
            return;
        }

        if (Request.isPndLstEmpty()) {
            User.messagePlayer(admin, Config.attend_failed_no_ticket);
            return;
        }

        // next
        if (playerName == null) {

            // removes from head of queue
            request = Request.getHeadOfPndLst();

            // player should exist because request exists
            player = Bukkit.getPlayer(request.getPlayerID());

        // select
        } else {

            // check if player is online
            player = Bukkit.getPlayer(playerName);

            if (player == null) {
                User.messagePlayer(admin, Config.attend_failed_not_exist);
                return;
            }

            // check if request exists
            request = Request.getPndRequest(player.getUniqueId());

            if (request == null) {
                User.messagePlayer(admin, Config.attend_failed_not_exist);
                return;
            }

            Request.removeFromPndLst(request);

        }

        playerName = request.getPlayerName(); // get real name

        // set the staff who honored the request
        request.setHandledBy(adminName);
        request.setHandledBy(adminID);

        // migrate Request to Attending List
        Request.addToAtdLst(adminID, request);

        // teleport admin to player
        if (Config.use_auto_teleport) {
            try {
                ((Player) admin).teleport(player);
                User.messagePlayer(admin, Config.teleport_passed
                        .replaceAll("<PLAYERNAME>", playerName));
            } catch (Exception e) {
                User.messagePlayer(admin, Config.teleport_failed);
            }
        }

        String ticketsRemain = Integer.toString(Request.getPndLstSize());

        // notify user
        for (String message : Config.attend_passed_notify_player) {
            User.messagePlayer(player, message
                    .replaceAll("<ADMINNAME>", adminName));
        }

        // notify staff
        for (String message : Config.attend_passed_notify_staff) {
            Admin.messageAdmins(message
                    .replaceAll("<ADMINNAME>", adminName)
                    .replaceAll("<PLAYERNAME>", playerName)
                    .replaceAll("<TICKETSREMAIN>", ticketsRemain));
        }

        // trigger custom commands
        if (Config.attend_passed_trigger_enable) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Config.attend_passed_trigger_command
                    .replaceAll("<PLAYERNAME>", playerName)
                    .replaceAll("<ADMINNAME>", adminName));
        }

    }


    public void teleport2Player(CommandSender admin) {

        UUID adminID = ((Player) admin).getUniqueId();

        // check if a staff is already handling a player
        if (!Request.inAtdLst(adminID)) {
            User.messagePlayer(admin, Config.teleport_failed);
            return;
        }

        Request request = Request.getAtdRequest(adminID);

        String playerName = request.getPlayerName();
        UUID playerID = request.getPlayerID();

        try {
            ((Player) admin).teleport(Bukkit.getPlayer(playerID));
            User.messagePlayer(admin, Config.teleport_passed
                    .replaceAll("<PLAYERNAME>", playerName));
        } catch (Exception e) {
            User.messagePlayer(admin, Config.teleport_failed);
        }

    }


    public void printTicketInfo(CommandSender admin) {

        UUID adminID = ((Player) admin).getUniqueId();

        if (!Request.inAtdLst(adminID)) {
            User.messagePlayer(admin, Config.info_failed);
            return;
        }

        Request request = Request.getAtdRequest(adminID);

        String playerName = request.getPlayerName();
        String comment = request.getComment();
        String time = new SimpleDateFormat("MM/dd/yy HH:mm")
                .format(new Date(request.getTimestamp() * 1000));

        for (String message : Config.info_passed) {
            User.messagePlayer(admin, message
                    .replaceAll("<PLAYERNAME>", playerName)
                    .replaceAll("<DETAILS>", comment)
                    .replaceAll("<DATETIME>", time));
        }

    }


    public void dropTicket(CommandSender admin) {

        String adminName = admin.getName();
        UUID adminID = ((Player) admin).getUniqueId();

        if (!Request.inAtdLst(adminID)) {
            User.messagePlayer(admin, Config.drop_failed);
            return;
        }

        Request request = Request.removeFromAtdLst(adminID);

        String playerName = request.getPlayerName();
        UUID playerID = request.getPlayerID();

        User.messagePlayer(admin, Config.drop_passed_notify_handler
                .replaceAll("<PLAYERNAME>", playerName));

        // try to inform the player
        Player player = Bukkit.getPlayer(playerID);

        User.messagePlayer(player, Config.drop_passed_notify_player
                .replaceAll("<ADMINNAME>", adminName));

    }


    public void transferTicket(CommandSender admin1, String admin2Name) {

        UUID admin1ID = ((Player) admin1).getUniqueId();

        // check admin1 status
        if (!Request.inAtdLst(admin1ID)) {
            User.messagePlayer(admin1, Config.transfer_failed
                    .replaceAll("<ADMINNAME>",admin2Name));
            return;
        }

        // check if admin2 is online
        Player admin2 = Bukkit.getPlayer(admin2Name);

        if (admin2 == null) {
            User.messagePlayer(admin1, Config.transfer_failed
                    .replaceAll("<ADMINNAME>",admin2Name));
            return;
        }

        admin2Name = admin2.getName(); // get real name
        UUID admin2ID = admin2.getUniqueId();

        // check if admin2 is admin or if admin2 is attending
        if (!Admin.isAdmin(admin2ID) || Request.inAtdLst(admin2ID)) {
            User.messagePlayer(admin1, Config.transfer_failed
                    .replaceAll("<ADMINNAME>",admin2Name));
            return;
        }

        Request request = Request.removeFromAtdLst(admin1ID);

        request.setHandledBy(admin2Name);
        request.setHandledBy(admin2ID);

        Request.addToAtdLst(admin2ID, request);

        String playerName = request.getPlayerName();
        UUID playerID = request.getPlayerID();
        Player player = Bukkit.getPlayer(playerID);

        String ticketsRemain = Integer.toString(Request.getPndLstSize());

        User.messagePlayer(admin1, Config.transfer_passed
                .replaceAll("<ADMINNAME>",admin2Name));

        for (String message : Config.attend_passed_notify_player) {
            User.messagePlayer(player, message
                    .replaceAll("<ADMINNAME>", admin2Name));
        }

        for (String message : Config.attend_passed_notify_staff) {
            Admin.messageAdmins(message
                    .replaceAll("<ADMINNAME>", admin2Name)
                    .replaceAll("<PLAYERNAME>", playerName)
                    .replaceAll("<TICKETSREMAIN>", ticketsRemain));
        }

        if (Config.attend_passed_trigger_enable) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Config.attend_passed_trigger_command
                    .replaceAll("<PLAYERNAME>", playerName)
                    .replaceAll("<ADMINNAME>", admin2Name));
        }

    }


    /**
     * Called when an admin attempt to set a request as solved.
     * The user is then asked if the help was satisfactory
     */

    public void closeTicket(CommandSender admin) {

        UUID adminID = ((Player) admin).getUniqueId();

        // Make sure admin is not attending other requests
        if (!Request.inAtdLst(adminID)) {
            User.messagePlayer(admin, Config.close_failed);
            return;
        }

        // Get and remove Request from Attending List
        Request request = Request.removeFromAtdLst(adminID);

        // Get player uuid
        UUID playerID = request.getPlayerID();

        // Add Request to Awaiting List
        Request.addToCptLst(playerID, request);

        // Notify admin of a successful operation
        User.messagePlayer(admin, Config.close_passed);

        // Set review scheduler
        if (Config.review_reminder_enable) {
            BukkitTask reminder = new Review(plugin, playerID).runReminder();
            Review.addToRmdLst(playerID, reminder);
        } else {
            promptFeedback(playerID);
        }

    }


    /**
     * Closes a request and disposes it. It also gives an admin an honor point based on
     * what the player rated the help received
     */

    public void giveFeedback(CommandSender sender, Boolean isSatisfactory) {

        String playerName = sender.getName();
        UUID playerID = ((Player) sender).getUniqueId();

        // check if a player has a request in the completed list awaiting a rating
        if (!Request.inCptLst(playerID)) {
            User.messagePlayer(sender, Config.review_failed);
            return;
        }

        // remove request from completed request list
        Request completedRequest = Request.removeFromCptLst(playerID);

        // stop the reminders
        Review.removePlayer(playerID);

        // increment counter
        Request.addCompletedToday();

        // add to database
        plugin.getDataSource().addRecord(completedRequest, isSatisfactory);

        // send player a message
        User.messagePlayer(sender, Config.review_received);

        // check if admin is online
        // this must be kept because we did not remove from Awaiting after admin log out
        Player admin = Bukkit.getPlayer(completedRequest.getHandledByID());

        if (admin == null) {
            return;
        }

        // give admin honor point based on ans
        if (isSatisfactory) {

            User.messagePlayer(admin, Config.review_upvote_notify_handler
                    .replaceAll("<PLAYERNAME>", playerName));

            // fireworks
            Firework fw = admin.getWorld().spawn(admin.getLocation(), Firework.class);
            FireworkMeta fwm = fw.getFireworkMeta();
            FireworkEffect effect = FireworkEffect.builder().trail(true).withColor(Color.LIME).with(FireworkEffect.Type.CREEPER).build();
            fwm.addEffect(effect);
            fwm.setPower(1);
            fw.setFireworkMeta(fwm);

        } else {

            User.messagePlayer(admin, Config.review_downvote_notify_handler
                    .replaceAll("<PLAYERNAME>", playerName));

        }

    }


    /**
     * Prompts a player to rate a request after an admin has handled it
     */

    public boolean promptFeedback(UUID playerID) {

        Player player = Bukkit.getPlayer(playerID);

        if (player == null) {
            return false;
        }

        String playerName = player.getName();

        User.messagePlayer(player, Config.review_prompt);

        if (Config.review_prompt_trigger_enable) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Config.review_prompt_trigger_command
                    .replaceAll("<PLAYERNAME>", playerName));
        }

        return true;

    }


    /** Removes Requests from Lists */

    public void purgeTicket(CommandSender sender, String option) {

        String amountPurged;
        option = option.toLowerCase();

        switch (option) {

            case "pending":
                amountPurged = Integer.toString(Request.getPndLstSize());
                Request.clearPndLst();
                break;
            case "attending":
                amountPurged = Integer.toString(Request.getAtdLstSize());
                Request.clearAtdLst();
                break;
            case "completing":
                amountPurged = Integer.toString(Request.getCptLstSize());
                Request.clearCptLst();
                Review.clearRmdLst();
                break;
            default:
                User.messagePlayer(sender, Config.incorrect_syntax);
                return;

        }

        for (String message : Config.purge_message) {
            User.messagePlayer(sender, message
                    .replaceAll("<AMOUNT>", amountPurged)
                    .replaceAll("<TYPE>", option));
        }

    }


    public void removeTicket(CommandSender sender, String playerName) {

        // Validate playerName
        Player player = Bukkit.getPlayer(playerName);
        UUID playerID;

        if (player == null) {
            User.messagePlayer(sender, Config.remove_failed
                    .replaceAll("<PLAYERNAME>", playerName));
            return;
        }

        playerName = player.getName(); // get the correct name
        playerID = player.getUniqueId();

        Request.removePlayer(playerID);
        Review.removePlayer(playerID);

        User.messagePlayer(sender, Config.remove_passed
                .replaceAll("<PLAYERNAME>", playerName));

    }


    /**
     * Attempts to cancel a request made my a player
     */

    public void cancelTicket(CommandSender sender) {

        UUID playerID = ((Player) sender).getUniqueId();

        switch (Request.getStatus(playerID)) {

            case 0:
                User.messagePlayer(sender, Config.cancel_failed_no_ticket);
                return;
            case 1:
                Request.removeFromPndLst(playerID);
                User.messagePlayer(sender, Config.cancel_passed_notify_player);
                return;
            case 2:
                User.messagePlayer(sender, Config.cancel_failed_attending);
                return;
            case 3:
                User.messagePlayer(sender, Config.cancel_failed_completing);

        }

    }


    /**
     * Queries and tells the player the position/status of his request
     */

    public void printTicketStatus(CommandSender sender) {

        UUID playerID = ((Player) sender).getUniqueId();

        if (Admin.isAdmin(playerID)) {
            if (Request.inAtdLst(playerID)) {
                User.messagePlayer(sender, Config.status_staff_attending);
            } else {
                User.messagePlayer(sender, Config.status_staff_not_attending);
            }
            return;
        }

        switch (Request.getStatus(playerID)) {

            case 0:
                User.messagePlayer(sender, Config.status_no_ticket);
                return;
            case 1:
                String posInPndLst = Integer.toString(Request.getPosInPndLst(playerID));
                User.messagePlayer(sender, Config.status_pending
                        .replaceAll("<POSITION>", posInPndLst));
                return;
            case 2:
                User.messagePlayer(sender, Config.status_attending);
                return;
            case 3:
                User.messagePlayer(sender, Config.status_completing);

        }
    }


    /**
     * Get all requests count. Group by statuses
     * Sends the message to the specified player
     */

    public void printTicketStats(CommandSender sender) {

        String pending = Integer.toString(Request.getPndLstSize());
        String attending = Integer.toString(Request.getAtdLstSize());
        String awaiting = Integer.toString(Request.getCptLstSize());
        String completed = Integer.toString(Request.getCompletedToday());

        int total = plugin.getDataSource().getTotalTicketCount(1);
        int upVote = plugin.getDataSource().getTotalTicketCount(2);
        int percent;

        if (total == 0 || upVote == 0) {
            percent = 0;
        } else {
            percent = upVote * 100 / total;
        }

        String totalS = Integer.toString(total);
        String percentS = Integer.toString(percent);

        for (String message : Config.stats_message) {
        	User.messagePlayer(sender, message.replaceAll("<AWAITING>", awaiting)
                    .replaceAll("<INPROGRESS>", attending)
                    .replaceAll("<INQUEUE>", pending)
                    .replaceAll("<COMPLETED>", completed)
                    .replaceAll("<TOTAL>", totalS)
                    .replaceAll("<PERCENT>", percentS));
    	}

    }


    // print hptop module
    public void printHonorTop(CommandSender sender, int limit) {

        String[][] honors = plugin.getDataSource().getTopHonors(limit);

        for (String message : Config.hptop_title) {
            User.messagePlayer(sender, message);
        }

        for (int i = 0; i < limit ; i++) {
            if (honors[i][0] != null) {
                User.messagePlayer(sender, Config.hptop_body
                        .replaceAll("<ADMINNAME>", honors[i][0])
                        .replaceAll("<UPVOTE>", honors[i][1])
                        .replaceAll("<DOWNVOTE>", honors[i][2])
                        .replaceAll("<TOTAL>", honors[i][3])
                        .replaceAll("<PERCENT>", honors[i][4]));
            }
        }

        for (String message : Config.hptop_footer) {
            User.messagePlayer(sender, message);
        }

    }


    public void printHonorStats(CommandSender sender, String adminName) {

        int upVote = plugin.getDataSource().getAdminTicketCount(adminName,1);
        int downVote = plugin.getDataSource().getAdminTicketCount(adminName,2);
        int total = upVote + downVote;
        int percent;

        if (total == 0 || upVote == 0) {
            percent = 0;
        } else {
             percent = (upVote*100/(total));
        }

        String upVoteS = Integer.toString(upVote);
        String downVoteS = Integer.toString(downVote);
        String totalS = Integer.toString(total);
        String percentS = Integer.toString(percent);

        for (String message : Config.hpstats_message) {
            User.messagePlayer(sender, message
                    .replaceAll("<UPVOTE>", upVoteS)
                    .replaceAll("<DOWNVOTE>", downVoteS)
                    .replaceAll("<TOTAL>", totalS)
                    .replaceAll("<PERCENT>", percentS)
                    .replaceAll("<ADMINNAME>", adminName));
        }

    }


    //print history module
    public void printHonorHistory(CommandSender sender, int limit) {

        String[][] history = plugin.getDataSource().getHistory(limit);
        String rating;

        for (String history_title : Config.history_title) {
            User.messagePlayer(sender, history_title);
        }

        for (int i = 0; i < limit; i++) {
            if (history[i][0] != null) {
                if (Long.parseLong(history[i][4])==0) {
                    rating = Config.history_downvote_indicator;
                } else {
                    rating = Config.history_upvote_indicator;
                }
                User.messagePlayer(sender, Config.history_body
                        .replaceAll("<PLAYERNAME>", history[i][0])
                        .replaceAll("<ADMINNAME>", history[i][1])
                        .replaceAll("<DETAILS>", history[i][2])
                        .replaceAll("<TIME>", new SimpleDateFormat("MM/dd/yy HH:mm").format(new Date(Long.parseLong(history[i][3])*1000)))
                        .replaceAll("<RATING>", rating));
            }
        }

        for (String history_footer : Config.history_footer) {
            User.messagePlayer(sender, history_footer);
        }

    }


}