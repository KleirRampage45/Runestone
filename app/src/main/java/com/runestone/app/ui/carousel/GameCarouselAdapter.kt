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
    private val onCardClicked: (GameCardInfo) -> Unit,
    private val onCardLongPressed: (GameCardInfo) -> Unit = {},
) : RecyclerView.Adapter<GameCarouselAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = CarouselGameCard(parent.context)
        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val game = games[position]
        holder.card.bind(game)
        holder.itemView.setOnClickListener { onCardClicked(game) }
        holder.itemView.setOnLongClickListener {
            onCardLongPressed(game)
            true
        }
    }

    override fun getItemCount() = games.size

    class ViewHolder(val card: CarouselGameCard) : RecyclerView.ViewHolder(card)
}
