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


typedef struct SPSContext {
    int buffer_index;
    int total_bytes;
    uint8_t * buffer;
    uint32_t current_word;
    int current_word_bits_left;
    int pic_width_in_mbs_minus1;
    int pic_height_in_map_units_minus1;
    int frame_crop_left_offset;
    int frame_crop_right_offset;
    int frame_crop_top_offset;
    int frame_crop_bottom_offset ;
    int frame_mbs_only_flag;
    int chroma_format_idc;
    int sar_width;
    int sar_height;
    int aspect_ratio;
    int error;
} SPSContext;

static void fillCurrentWord(SPSContext *ctx) {
    int buffer_bytes_left = ctx->total_bytes - ctx->buffer_index;
    if (ctx->error < 0)
        return;

    if (buffer_bytes_left <= 0) {
        ctx->error = buffer_bytes_left;
        return;
    }

    if (buffer_bytes_left >= 4) {
        ctx->current_word = ntohl(*(uint32_t*)(ctx->buffer + ctx->buffer_index));
        ctx->buffer_index += 4;
        ctx->current_word_bits_left += 32;
    } else {
        while (buffer_bytes_left != 0) {
            ctx->current_word <<= 8;
            ctx->current_word |= ctx->buffer[ctx->buffer_index];
            ctx->buffer_index++;
            buffer_bytes_left--;
            ctx->current_word_bits_left += 8;
        }
    }
}

static int skipLeadingZero(SPSContext *ctx) {
    int i;

    if (ctx->error < 0)
        return -1;
    for (i = 0; i < ctx->current_word_bits_left; i++) {
        if ((ctx->current_word & (0x80000000 >> i)) != 0) {
            ctx->current_word <<= i;
            ctx->current_word_bits_left -= i;
            return  i;
        }
    }

    fillCurrentWord(ctx);

    return  skipLeadingZero(ctx) + i;
}

static int readBits(SPSContext *ctx, int bits) {
    if (ctx->error < 0)
        return -1;

    if (bits <= ctx->current_word_bits_left) {
        int result = ctx->current_word >> (32 - bits);
        ctx->current_word <<= bits;
        ctx->current_word_bits_left -= bits;
        return result;
    }

    int result = 0;
    if (ctx->current_word_bits_left != 0) {
        result = ctx->current_word;
    }

    result = result >> (32 - ctx->current_word_bits_left);

    int bits_need_left = bits - ctx->current_word_bits_left;

    fillCurrentWord(ctx);

    int bits_need_next = bits_need_left <= ctx->current_word_bits_left ?
    bits_need_left : ctx->current_word_bits_left;

    int result2 = ctx->current_word >> (32 - bits_need_next);
    ctx->current_word <<= bits_need_next;
    ctx->current_word_bits_left -= bits_need_next;

    result = (result << bits_need_next) | result2;

    return  result;
}

static int readUEG(SPSContext *ctx) {
    if (ctx->error < 0)
        return -1;

    return readBits(ctx, skipLeadingZero(ctx) + 1) - 1;
}


static int readSEG(SPSContext *ctx) {
    if (ctx->error < 0)
        return -1;
    int result = readUEG(ctx);
    if ((result & 0x01) == 0x01) {
        return (result + 1) >> 1;
    } else {
        return -1 * (result >> 1);
    }
}

static void skipScalingList(SPSContext *ctx, int count) {
    int last_scale = 8;
    int next_scale = 8;
    int delta_scale;
    if (ctx->error < 0)
        return;
    for (int i = 0; i < count; i++) {
        if (next_scale != 0) {
            delta_scale = readSEG(ctx);
            next_scale = (last_scale + delta_scale + 256) % 256;
        }
        last_scale = (next_scale == 0) ? last_scale : next_scale;
    }
}




IJKFFResolution parseSPS(SPSContext *ctx) {
    const int sar_w_table[] = {1, 12, 10, 16, 40, 24, 20, 32, 80, 18, 15, 64, 160, 4, 3, 2};
    const int sar_h_table[] = {1, 11, 11, 11, 33, 11, 11, 11, 33, 11, 11, 33,  99, 3, 2, 1};
    /*
     * unsigned int(8) configurationVersion = 1;
     * unsigned int(8) AVCProfileIndication;
     * unsigned int(8) profile_compatibility;
     * unsigned int(8) AVCLevelIndication;
     * bit(6) reserved = ‘111111’b;
     * unsigned int(2) lengthSizeMinusOne;
     * bit(3) reserved = ‘111’b;
     * unsigned int(5) numOfSequenceParameterSets;
     *
     */

    /*
     * for (i=0; i< numOfSequenceParameterSets; i++) {
     *   unsigned int(16) sequenceParameterSetLength ;
     *   bit(8*sequenceParameterSetLength) sequenceParameterSetNALUnit;
     * }
     */
    readBits(ctx, 32);
    readBits(ctx, 32);

    //0x67  NALU type
    readBits(ctx, 8);
    //profile_idc
    int profile_idc = readBits(ctx, 8);
    readBits(ctx, 8);
    readBits(ctx, 8);

    readUEG(ctx);

    if (profile_idc == 100 || profile_idc == 110 || profile_idc == 122 || profile_idc == 122 ||
        profile_idc == 244 || profile_idc == 44 || profile_idc == 83 || profile_idc == 86 ||
        profile_idc == 118 || profile_idc == 128 || profile_idc == 138 || profile_idc == 139 ||
        profile_idc == 134 || profile_idc == 135) {
        ctx->chroma_format_idc = readUEG(ctx);
        if (ctx->chroma_format_idc == 3) {
            readBits(ctx, 1);
        }
        readUEG(ctx);
        readUEG(ctx);
        readBits(ctx, 1);
        if (readBits(ctx, 1) != 0) {
            for (int i = 0; i < ((ctx->chroma_format_idc != 3) ? 8 : 12); i++) {
                if (readBits(ctx, 1) == 1) {
                    if (i < 6) {
                        skipScalingList(ctx, 16);
                    } else {
                        skipScalingList(ctx, 64);
                    }
                }
            }
        }
    }

    readUEG(ctx);
    int pic_order_cnt_type = readUEG(ctx);
    if (pic_order_cnt_type == 0) {
        readUEG(ctx);
    } else if (pic_order_cnt_type == 1) {
        readBits(ctx, 1);
        readSEG(ctx);
        readSEG(ctx);
        int num_ref_frames_in_pic_order_cnt_cycle = readUEG(ctx);
        for (int i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; i++) {
            readSEG(ctx);
        }
    }
    readUEG(ctx);
    readBits(ctx, 1);

    ctx->pic_width_in_mbs_minus1 = readUEG(ctx);
    ctx->pic_height_in_map_units_minus1 = readUEG(ctx);
    ctx->frame_mbs_only_flag = readBits(ctx, 1);
    if (ctx->frame_mbs_only_flag  == 0) {
        readBits(ctx, 1);
    }

    readBits(ctx, 1);
    if (readBits(ctx, 1) != 0) {
        ctx->frame_crop_left_offset = readUEG(ctx);
        ctx->frame_crop_right_offset = readUEG(ctx);
        ctx->frame_crop_top_offset = readUEG(ctx);
        ctx->frame_crop_bottom_offset = readUEG(ctx);
    }
    if (readBits(ctx, 1) != 0) {
        if(readBits(ctx, 1) != 0) {
            ctx->aspect_ratio = readBits(ctx, 8);
            printf("sar_idc = %d\n", ctx->aspect_ratio);
            if (ctx->aspect_ratio == 255) {
                ctx->sar_width = readBits(ctx, 16);
                ctx->sar_height = readBits(ctx, 16);
            }
        }
    } else {
        ctx->aspect_ratio = 0;
    }

    // Caculate data
    IJKFFResolution sps = {0};
    if (ctx->error < 0)
        return sps;
    
    int width_count = 2;
    if (ctx->chroma_format_idc == 0 || ctx->chroma_format_idc == 3) {
        width_count = 1;
    }
    sps.width = (ctx->pic_width_in_mbs_minus1 + 1) * 16 -
    (ctx->frame_crop_left_offset + ctx->frame_crop_right_offset) * width_count;
    
    int height_count = 1;
    if (ctx->chroma_format_idc == 1) {
        height_count = 2;
    }

    sps.height = (2 - ctx->frame_mbs_only_flag) * ((ctx->pic_height_in_map_units_minus1 + 1) * 16) -
    (ctx->frame_crop_top_offset + ctx->frame_crop_bottom_offset) *
    (2 - ctx->frame_mbs_only_flag) * height_count;


    if (ctx->aspect_ratio > 0 && ctx->aspect_ratio <= 16) {
        sps.sar.num = (double)sar_w_table[ctx->aspect_ratio - 1];
        sps.sar.den = sar_h_table[ctx->aspect_ratio - 1];
    } else if (ctx->aspect_ratio == 255){
        sps.sar.num = ctx->sar_width;
        sps.sar.den = ctx->sar_height;
    } else {
        sps.sar.num = 1;
        sps.sar.den = 1;
    }
    
    return  sps;
}

+(IJKFFResolution) getResolutionByExtradata:(NSString *) extradata_base64
{
    SPSContext ctx = {0};
    NSData *data = [[NSData alloc] initWithBase64EncodedString:extradata_base64
                                                       options:NSDataBase64DecodingIgnoreUnknownCharacters];

    ctx.buffer =  [data bytes];
    ctx.total_bytes = [data length];
    return parseSPS(&ctx);
}
@end
