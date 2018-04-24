package tv.danmaku.ijk.media.player;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/**
 * Created by moeyard on 23/4/2018.
 */

// Support H.264 201602 version
public class SPSParser {
    public  static SPS parseExtradata(String extradata_base64) {
        SPSParser parser = new SPSParser(extradata_base64);
        return  parser.parseSPS();
    }
    public class SPS {
        public int pic_width_in_mbs_minus1;
        public int pic_height_in_map_units_minus1;
        public int frame_crop_left_offset;
        public int frame_crop_right_offset;
        public int frame_crop_top_offset;
        public int frame_crop_bottom_offset ;
        public int frame_mbs_only_flag;
        public int chroma_format_idc;
        public int sar_width;
        public int sar_height;
        public int aspect_ratio;
        private int [] sar_w_table = {1, 12, 10, 16, 40, 24, 20, 32, 80, 18, 15, 64, 160, 4, 3, 2};
        private int [] sar_h_table = {1, 11, 11, 11, 33, 11, 11, 11, 33, 11, 11, 33,  99, 3, 2, 1};
        public int getWidth() {
            int count = 2;
            if (chroma_format_idc == 0 || chroma_format_idc == 3) {
                count = 1;
            }
            return (pic_width_in_mbs_minus1 + 1) * 16 - (frame_crop_left_offset + frame_crop_right_offset) * count;
        }

        public int getHeight() {
            int count = 1;
            if (chroma_format_idc == 1) {
                count = 2;
            }

            return (2 - frame_mbs_only_flag) * ((pic_height_in_map_units_minus1 + 1) * 16) -
                    (frame_crop_top_offset + frame_crop_bottom_offset) * (2 - frame_mbs_only_flag) * count;
        }

        public double getSarScale() {
            double scale;
            if (aspect_ratio > 0 && aspect_ratio <= 16) {
                scale = sar_w_table[aspect_ratio - 1] / sar_h_table[aspect_ratio - 1];
            } else if (aspect_ratio == 255){
                scale = sar_width / sar_height;
            } else {
                scale = 1;
            }
            return scale;
        }

        public String toString(){
            return "Width = " + getWidth() + " Height = " + getHeight() +
                    " SarScale= " + getSarScale();
        }
    }


    private DataInputStream buffer;
    private int buffer_index;
    private int total_bytes;
    private int current_word;
    private int current_word_bits_left;




    public SPSParser(String extradata_base64) {
        byte [] sps = Base64.decode(extradata_base64, Base64.DEFAULT);
        buffer = new DataInputStream(new ByteArrayInputStream(sps));
        buffer_index = 0;
        total_bytes = sps.length;
        current_word = 0;
        current_word_bits_left = 0;

    }

    private  void fillCurrentWord() {
        int buffer_bytes_left = total_bytes - buffer_index;

        if (buffer_bytes_left <= 0) {
            return;
        }

        int bytes_read = Math.min(4, buffer_bytes_left);

        try {
            if (bytes_read == 4) {
                current_word = buffer.readInt();
            } else {
                current_word = 0;
                while (bytes_read != 0) {
                    current_word <<= 8;
                    current_word |= buffer.readUnsignedByte();
                    bytes_read -= 1;
                }
            }
        } catch (java.io.IOException err1) {
            //TODO: error handle
            return;
        }
        buffer_index += bytes_read;
        current_word_bits_left = bytes_read * 8;
    }

    private void skipScalingList(int count) {
        int last_scale = 8;
        int next_scale = 8;
        int delta_scale;
        for (int i = 0; i < count; i++) {
            if (next_scale != 0) {
                delta_scale = readSEG();
                next_scale = (last_scale + delta_scale + 256) % 256;
            }
            last_scale = (next_scale == 0) ? last_scale : next_scale;
        }
    }
    private  int skipLeadingZero() {
        int i;

        for (i = 0; i < current_word_bits_left; i++) {
            if ((current_word & (0x80000000 >>> i)) != 0) {
                current_word <<= i;
                current_word_bits_left -= i;
                return  i;
            }
        }

        fillCurrentWord();

        return  skipLeadingZero() + i;
    }
    private  int readBits(int bits) {
        if (bits <= current_word_bits_left) {
            int result = current_word >>> (32 - bits);
            current_word <<= bits;
            current_word_bits_left -= bits;
            return result;
        }

        int result = 0;
        if (current_word_bits_left != 0) {
            result = current_word;
        }

        result = result >>> (32 - current_word_bits_left);

        int bits_need_left = bits - current_word_bits_left;

        fillCurrentWord();
        int bits_need_next = Math.min(bits_need_left, current_word_bits_left);

        int result2 = current_word >>> (32 - bits_need_next);
        current_word <<= bits_need_next;
        current_word_bits_left -= bits_need_next;

        result = (result << bits_need_next) | result2;

        return  result;
    }


    private int readUEG() {
        return readBits(skipLeadingZero() + 1) - 1;
    }


    private  int readSEG() {
        int result = readUEG();
        if ((result & 0x01) == 0x01) {
            return (result + 1) >>> 1;
        } else {
            return -1 * (result >>> 1);
        }
    }

    private SPS parseSPS() {
        SPS sps = new SPS();
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
        readBits(32);
        readBits(32);

        //0x67  NALU type
        readBits(8);
        //profile_idc
        int profile_idc = readBits(8);
        readBits(8);
        int level_idc = readBits(8);
        readUEG();

        if (profile_idc == 100 || profile_idc == 110 || profile_idc == 122 || profile_idc == 122 ||
                profile_idc == 244 || profile_idc == 44 || profile_idc == 83 || profile_idc == 86 ||
                profile_idc == 118 || profile_idc == 128 || profile_idc == 138 || profile_idc == 139 ||
                profile_idc == 134 || profile_idc == 135) {
            sps.chroma_format_idc = readUEG();
            if (sps.chroma_format_idc == 3) {
                readBits(1);
            }
            readUEG();
            readUEG();
            readBits(1);
            if (readBits(1) != 0) {
                for (int i = 0; i < ((sps.chroma_format_idc != 3) ? 8 : 12); i++) {
                    if (readBits(1) == 1) {
                        if (i < 6) {
                            skipScalingList(16);
                        } else {
                            skipScalingList(64);
                        }
                    }
                }
            }
        }

        readUEG();
        int pic_order_cnt_type = readUEG();
        if (pic_order_cnt_type == 0) {
            readUEG();
        } else if (pic_order_cnt_type == 1) {
            readBits(1);
            readSEG();
            readSEG();
            int num_ref_frames_in_pic_order_cnt_cycle = readUEG();
            for (int i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; i++) {
                readSEG();
            }
        }
        readUEG();
        readBits(1);

        sps.pic_width_in_mbs_minus1 = readUEG();
        sps.pic_height_in_map_units_minus1 = readUEG();
        sps.frame_mbs_only_flag = readBits(1);
        if (sps.frame_mbs_only_flag  == 0) {
            readBits(1);
        }

        readBits(1);
        if (readBits(1) != 0) {
            sps.frame_crop_left_offset = readUEG();
            sps.frame_crop_right_offset = readUEG();
            sps.frame_crop_top_offset = readUEG();
            sps.frame_crop_bottom_offset = readUEG();
        }
        if (readBits(1) != 0) {
            if(readBits(1) != 0) {
                sps.aspect_ratio = readBits(8);
                if (sps.aspect_ratio == 255) {
                    sps.sar_width = readBits(16);
                    sps.sar_height = readBits(16);
                }
            }
        } else {
            sps.aspect_ratio = 0;
        }

        return  sps;
    }
}
