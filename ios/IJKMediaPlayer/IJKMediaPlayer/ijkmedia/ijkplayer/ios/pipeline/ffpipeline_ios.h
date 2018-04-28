/*
 * ffpipeline_ios.h
 *
 * Copyright (c) 2014 Zhou Quan <zhouqicy@gmail.com>
 *
 * This file is part of ijkPlayer.
 *
 * ijkPlayer is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * ijkPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with ijkPlayer; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#ifndef FFPLAY__FF_FFPIPELINE_IOS_H
#define FFPLAY__FF_FFPIPELINE_IOS_H

#include "ijkplayer/ff_ffpipeline.h"

#define VIDEOTOOLBOX_UNKNOWN_ERROR  -1
#define VIDEOTOOLBOX_RECOVERY_ERROR -2
#define VIDEOTOOLBOX_RECOVERY_FAIL -3
#define VIDEOTOOLBOX_DECODEC_ERROR -4

struct FFPlayer;

IJKFF_Pipeline *ffpipeline_create_from_ios(struct FFPlayer *ffp);

#endif
