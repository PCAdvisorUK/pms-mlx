/*
 * PS3 Media Server, for streaming any medias to your PS3.
 * Copyright (C) 2008  A.Brochard
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.pms.dlna;

import net.pms.configuration.DLNAResourceConfiguration;
import net.pms.formats.Format;

// FIXME (breaking change): rename VideoFeed
public class VideosFeed extends Feed {
	@Override
	protected void manageItem() {
		WebVideoStream fi = new WebVideoStream(getTempItemTitle(), getTempItemLink(), getTempItemThumbURL(), getDLNAResourceConfiguration());
		addChild(fi);
	}

	public VideosFeed(String url, DLNAResourceConfiguration configuration) {
		super("" + System.currentTimeMillis(), url, Format.VIDEO, configuration);
	}
}
