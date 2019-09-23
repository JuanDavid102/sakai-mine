package edu.uc.ltigradebook.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BannerUser {

    String SPRIDEN_LAST_NAME;
    String SPRIDEN_FIRST_NAME;
    String SPRIDEN_ID;
    String SFRSTCR_CRN;
    String SFRSTCR_GRDE_CODE;
    String SSBSECT_CRSE_NUMB;
    String SSBSECT_SUBJ_CODE;
    String SSBSECT_SEQ_NUMB;
    String userRut;

}
