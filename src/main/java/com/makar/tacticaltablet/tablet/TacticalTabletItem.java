package com.makar.tacticaltablet.tablet;

import com.makar.tacticaltablet.game.lobby.LobbyManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TacticalTabletItem extends Item {

    public TacticalTabletItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            LobbyManager.sync(sp);
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}

