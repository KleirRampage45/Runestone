/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.ui.carousel

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.runestone.app.ui.GameCardInfo

class GameCarouselAdapter(
    private val games: List<GameCardInfo>,
    private val onPlay: (GameCardInfo) -> Unit,
    private val onSettings: (GameCardInfo) -> Unit,
    private val onCardLongPressed: (GameCardInfo) -> Unit = {},
) : RecyclerView.Adapter<GameCarouselAdapter.ViewHolder>() {

    private var focusedPosition = RecyclerView.NO_POSITION
    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = CarouselGameCard(parent.context)
        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val game = games[position]
        holder.card.bind(
            game = game,
            showActions = position == selectedPosition,
            onPlay = onPlay,
            onSettings = onSettings,
        )
        holder.itemView.isSelected = position == focusedPosition
        holder.itemView.setOnClickListener {
            setSelectedPosition(if (position == selectedPosition) RecyclerView.NO_POSITION else position)
        }
        holder.itemView.setOnLongClickListener {
            onCardLongPressed(game)
            true
        }
    }

    override fun getItemCount() = games.size

    fun getGame(position: Int): GameCardInfo? = games.getOrNull(position)

    fun setFocusedPosition(position: Int) {
        if (position == focusedPosition) return
        val previousPosition = focusedPosition
        focusedPosition = position
        if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition)
        if (focusedPosition != RecyclerView.NO_POSITION) notifyItemChanged(focusedPosition)
    }

    fun clearSelection() {
        setSelectedPosition(RecyclerView.NO_POSITION)
    }

    private fun setSelectedPosition(position: Int) {
        if (position == selectedPosition) return
        val previousPosition = selectedPosition
        selectedPosition = position
        if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)
    }

    class ViewHolder(val card: CarouselGameCard) : RecyclerView.ViewHolder(card)
}
