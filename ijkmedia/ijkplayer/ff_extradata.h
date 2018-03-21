/*
 * ff_extradata.h
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

#ifndef FFPLAY__FF_EXTRADATA_H
#define FFPLAY__FF_EXTRADATA_H
//re-use internal structure and function
struct AVStreamInternal {
    /**
     * Set to 1 if the codec allows reordering, so pts can be different
     * from dts.
     */
    int reorder;

    /**
     * bitstream filters to run on stream
     * - encoding: Set by muxer using ff_stream_add_bitstream_filter
     * - decoding: unused
     */
    AVBSFContext **bsfcs;
    int nb_bsfcs;

    /**
     * Whether or not check_bitstream should still be run on each packet
     */
    int bitstream_checked;

    /**
     * The codec context used by avformat_find_stream_info, the parser, etc.
     */
    AVCodecContext *avctx;
    /**
     * 1 if avctx has been initialized with the values from the codec parameters
     */
    int avctx_inited;

    enum AVCodecID orig_codec_id;

    /* the context for extracting extradata in find_stream_info()
     * inited=1/bsf=NULL signals that extracting is not possible (codec not
     * supported) */
    struct {
        AVBSFContext *bsf;
        AVPacket     *pkt;
        int inited;
    } extract_extradata;

    /**
     * Whether the internal avctx needs to be updated from codecpar (after a late change to codecpar)
     */
    int need_context_update;
};

struct AVFormatInternal {
    /**
     * Number of streams relevant for interleaving.
     * Muxing only.
     */
    int nb_interleaved_streams;

    /**
     * This buffer is only needed when packets were already buffered but
     * not decoded, for example to get the codec parameters in MPEG
     * streams.
     */
    struct AVPacketList *packet_buffer;
    struct AVPacketList *packet_buffer_end;

    /* av_seek_frame() support */
    int64_t data_offset; /**< offset of the first packet */

    /**
     * Raw packets from the demuxer, prior to parsing and decoding.
     * This buffer is used for buffering packets until the codec can
     * be identified, as parsing cannot be done without knowing the
     * codec.
     */
    struct AVPacketList *raw_packet_buffer;
    struct AVPacketList *raw_packet_buffer_end;
    /**
     * Packets split by the parser get queued here.
     */
    struct AVPacketList *parse_queue;
    struct AVPacketList *parse_queue_end;
    /**
     * Remaining size available for raw_packet_buffer, in bytes.
     */
#define RAW_PACKET_BUFFER_SIZE 2500000
    int raw_packet_buffer_remaining_size;

    /**
     * Offset to remap timestamps to be non-negative.
     * Expressed in timebase units.
     * @see AVStream.mux_ts_offset
     */
    int64_t offset;

    /**
     * Timebase for the timestamp offset.
     */
    AVRational offset_timebase;

#if FF_API_COMPUTE_PKT_FIELDS2
    int missing_ts_warning;
#endif

    int inject_global_side_data;

    int avoid_negative_ts_use_pts;

    /**
     * Whether or not a header has already been written
     */
    int header_written;
    int write_header_ret;

    /**
     * Timestamp of the end of the shortest stream.
     */
    int64_t shortest_end;

    /**
     * Whether or not avformat_init_output has already been called
     */
    int initialized;

    /**
     * Whether or not avformat_init_output fully initialized streams
     */
    int streams_initialized;

    /**
     * ID3v2 tag useful for MP3 demuxing
     */
    AVDictionary *id3v2_meta;

    /*
     * Prefer the codec framerate for avg_frame_rate computation.
     */
    int prefer_codec_framerate;
};




#define RESYNC_BUFFER_SIZE (1<<20)
enum {
    FLV_STREAM_TYPE_VIDEO,
    FLV_STREAM_TYPE_AUDIO,
    FLV_STREAM_TYPE_DATA,
    FLV_STREAM_TYPE_NB,
};
typedef struct FLVContext {
    const AVClass *class; ///< Class for private options.
    int trust_metadata;   ///< configure streams according onMetaData
    int wrong_dts;        ///< wrong dts due to negative cts
    uint8_t *new_extradata[FLV_STREAM_TYPE_NB];
    int new_extradata_size[FLV_STREAM_TYPE_NB];
    int last_sample_rate;
    int last_channels;
    struct {
        int64_t dts;
        int64_t pos;
    } validate_index[2];
    int validate_next;
    int validate_count;
    int searched_for_end;

    uint8_t resync_buffer[2*RESYNC_BUFFER_SIZE];

    int broken_sizes;
    int sum_flv_tag_size;

    int last_keyframe_stream_index;
    int keyframe_count;
    int64_t video_bit_rate;
    int64_t audio_bit_rate;
    int64_t *keyframe_times;
    int64_t *keyframe_filepositions;
    int missing_streams;
    AVRational framerate;
} FLVContext;

#define QP_MAX_NUM (51 + 6*6)
#define MAX_SPS_COUNT          32
#define MAX_PPS_COUNT         256

typedef struct SPS {
    unsigned int sps_id;
    int profile_idc;
    int level_idc;
    int chroma_format_idc;
    int transform_bypass;              ///< qpprime_y_zero_transform_bypass_flag
    int log2_max_frame_num;            ///< log2_max_frame_num_minus4 + 4
    int poc_type;                      ///< pic_order_cnt_type
    int log2_max_poc_lsb;              ///< log2_max_pic_order_cnt_lsb_minus4
    int delta_pic_order_always_zero_flag;
    int offset_for_non_ref_pic;
    int offset_for_top_to_bottom_field;
    int poc_cycle_length;              ///< num_ref_frames_in_pic_order_cnt_cycle
    int ref_frame_count;               ///< num_ref_frames
    int gaps_in_frame_num_allowed_flag;
    int mb_width;                      ///< pic_width_in_mbs_minus1 + 1
    ///< (pic_height_in_map_units_minus1 + 1) * (2 - frame_mbs_only_flag)
    int mb_height;
    int frame_mbs_only_flag;
    int mb_aff;                        ///< mb_adaptive_frame_field_flag
    int direct_8x8_inference_flag;
    int crop;                          ///< frame_cropping_flag

    /* those 4 are already in luma samples */
    unsigned int crop_left;            ///< frame_cropping_rect_left_offset
    unsigned int crop_right;           ///< frame_cropping_rect_right_offset
    unsigned int crop_top;             ///< frame_cropping_rect_top_offset
    unsigned int crop_bottom;          ///< frame_cropping_rect_bottom_offset
    int vui_parameters_present_flag;
    AVRational sar;
    int video_signal_type_present_flag;
    int full_range;
    int colour_description_present_flag;
    enum AVColorPrimaries color_primaries;
    enum AVColorTransferCharacteristic color_trc;
    enum AVColorSpace colorspace;
    int timing_info_present_flag;
    uint32_t num_units_in_tick;
    uint32_t time_scale;
    int fixed_frame_rate_flag;
    short offset_for_ref_frame[256]; // FIXME dyn aloc?
    int bitstream_restriction_flag;
    int num_reorder_frames;
    int scaling_matrix_present;
    uint8_t scaling_matrix4[6][16];
    uint8_t scaling_matrix8[6][64];
    int nal_hrd_parameters_present_flag;
    int vcl_hrd_parameters_present_flag;
    int pic_struct_present_flag;
    int time_offset_length;
    int cpb_cnt;                          ///< See H.264 E.1.2
    int initial_cpb_removal_delay_length; ///< initial_cpb_removal_delay_length_minus1 + 1
    int cpb_removal_delay_length;         ///< cpb_removal_delay_length_minus1 + 1
    int dpb_output_delay_length;          ///< dpb_output_delay_length_minus1 + 1
    int bit_depth_luma;                   ///< bit_depth_luma_minus8 + 8
    int bit_depth_chroma;                 ///< bit_depth_chroma_minus8 + 8
    int residual_color_transform_flag;    ///< residual_colour_transform_flag
    int constraint_set_flags;             ///< constraint_set[0-3]_flag
    uint8_t data[4096];
    size_t data_size;
} SPS;

typedef struct PPS {
    unsigned int sps_id;
    int cabac;                  ///< entropy_coding_mode_flag
    int pic_order_present;      ///< pic_order_present_flag
    int slice_group_count;      ///< num_slice_groups_minus1 + 1
    int mb_slice_group_map_type;
    unsigned int ref_count[2];  ///< num_ref_idx_l0/1_active_minus1 + 1
    int weighted_pred;          ///< weighted_pred_flag
    int weighted_bipred_idc;
    int init_qp;                ///< pic_init_qp_minus26 + 26
    int init_qs;                ///< pic_init_qs_minus26 + 26
    int chroma_qp_index_offset[2];
    int deblocking_filter_parameters_present; ///< deblocking_filter_parameters_present_flag
    int constrained_intra_pred;     ///< constrained_intra_pred_flag
    int redundant_pic_cnt_present;  ///< redundant_pic_cnt_present_flag
    int transform_8x8_mode;         ///< transform_8x8_mode_flag
    uint8_t scaling_matrix4[6][16];
    uint8_t scaling_matrix8[6][64];
    uint8_t chroma_qp_table[2][QP_MAX_NUM+1];  ///< pre-scaled (with chroma_qp_index_offset) version of qp_table
    int chroma_qp_diff;
    uint8_t data[4096];
    size_t data_size;

    uint32_t dequant4_buffer[6][QP_MAX_NUM + 1][16];
    uint32_t dequant8_buffer[6][QP_MAX_NUM + 1][64];
    uint32_t(*dequant4_coeff[6])[16];
    uint32_t(*dequant8_coeff[6])[64];
} PPS;

typedef struct H264ParamSets {
    AVBufferRef *sps_list[MAX_SPS_COUNT];
    AVBufferRef *pps_list[MAX_PPS_COUNT];

    AVBufferRef *pps_ref;
    AVBufferRef *sps_ref;
    /* currently active parameters sets */
    const PPS *pps;
    const SPS *sps;
} H264ParamSets;

extern int ff_h264_decode_extradata(const uint8_t *data, int size, H264ParamSets *ps,
                                    int *is_avc, int *nal_length_size,
                                    int err_recognition, void *logctx);

typedef struct MPEG4AudioConfig {
    int object_type;
    int sampling_index;
    int sample_rate;
    int chan_config;
    int sbr; ///< -1 implicit, 1 presence
    int ext_object_type;
    int ext_sampling_index;
    int ext_sample_rate;
    int ext_chan_config;
    int channels;
    int ps;  ///< -1 implicit, 1 presence
    int frame_length_short;
} MPEG4AudioConfig;

extern int avpriv_mpeg4audio_get_config(MPEG4AudioConfig *c, const uint8_t *buf,
                                 int bit_size, int sync_extension);
struct ConcatStream;
typedef struct ConcatStream ConcatStream;

typedef struct {
    char *url;
    int64_t start_time;
    int64_t file_start_time;
    int64_t file_inpoint;
    int64_t duration;
    int64_t next_dts;
    ConcatStream *streams;
    int64_t inpoint;
    int64_t outpoint;
    AVDictionary *metadata;
    int nb_streams;
} ConcatFile;



typedef enum ConcatMatchMode {
    MATCH_EXACT_ID,
} ConcatMatchMode;

typedef struct {
    AVClass *class;
    ConcatFile *files;
    ConcatFile *cur_file;
    unsigned nb_files;
    AVFormatContext *avf;
    int safe;
    int seekable;
    int eof;
    ConcatMatchMode stream_match_mode;
    unsigned auto_convert;
    int segment_time_metadata;
    AVDictionary *options;
    int error;
} ConcatContext;


extern void avpriv_set_pts_info(AVStream *s, int pts_wrap_bits,
                         unsigned int pts_num, unsigned int pts_den) ;

#define H264_NUIT_FIELD_BASED_FLAG 1

#endif
