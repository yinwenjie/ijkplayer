/*
 * IJKFFUtils.m
 *
 * Copyright (c) 2013-2018 Bilibili
 * Copyright (c) 2013-2018 Wu Zhiqiang <mymoeyard@gmail.com>
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
#include "IJKFFUtils.h"
#include "ff_ffinc.h"
#import <Foundation/Foundation.h>
@implementation IJKFFUtils {
}

+(IJKFFResolution) getResolutionByExtradata:(NSString *) extradata_base64
{
    IJKFFResolution res;
    H264ParamSets ps;
    const PPS *pps = NULL;
    const SPS *sps = NULL;
    uint8_t * extradata = NULL;
    memset(&ps, 0, sizeof(ps));
    memset(&res, 0, sizeof(res));
    int is_avc=0;
    int nal_length_size=0;
    int i;
    const char * extradata_enc = [extradata_base64 UTF8String];
    if (!extradata_enc)
        goto fail;
    
    //Init Video Stream
    size_t extradata_size = AV_BASE64_DECODE_SIZE(strlen(extradata_enc)) + AV_INPUT_BUFFER_PADDING_SIZE;
    extradata = av_mallocz(extradata_size);
    size_t r_extradata_size = av_base64_decode(extradata, extradata_enc, (int)extradata_size);
    
    if ((ff_h264_decode_extradata(extradata, (int)r_extradata_size, &ps,
                                  &is_avc, &nal_length_size,
                                  0, NULL)) < 0)
        goto fail;
    
    for (i = 0; i < MAX_PPS_COUNT; i++) {
        if (ps.pps_list[i]) {
            pps = (const PPS*)ps.pps_list[i]->data;
            break;
        }
    }
    
    if (pps)
        if (ps.sps_list[pps->sps_id])
            sps = (const SPS*)ps.sps_list[pps->sps_id]->data;
    
    if (pps && sps) {
        res.width = sps->mb_width  * 16 - (sps->crop_right + sps->crop_left);
        res.height = sps->mb_height * 16 - (sps->crop_top   + sps->crop_bottom);
        res.sar.num = sps->sar.num;
        res.sar.den = sps->sar.den;
    } else
        goto fail;
    
    av_log(NULL, AV_LOG_DEBUG, "width = %d, height = %d\n", (int)res.width, (int)res.height);
    av_log(NULL, AV_LOG_DEBUG, "sar = %d/%d\n", (int)res.sar.num, (int)res.sar.den);
    
fail:
    if (extradata)
        av_freep(&extradata);
    return res;
}

@end
