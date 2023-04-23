/*
 * Copyright (C) 2016-2023 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.gmt2001.datastore2.tablebuilder;

/**
 * Provides a fluent interface to define a table, then create or alter it in the database
 *
 * @author gmt2001
 */
public class TableBuilder {
    private final String name;

    /**
     * Constructor
     *
     * @param name The name of the table
     */
    public TableBuilder(String name) {
        this.name = name;
    }
}
