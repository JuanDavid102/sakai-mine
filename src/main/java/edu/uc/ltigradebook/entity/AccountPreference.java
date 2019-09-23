package edu.uc.ltigradebook.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "gradebook_account_preferences")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class AccountPreference {

    @Id
    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(name = "banner_enabled", nullable = false)
    private boolean bannerEnabled;

    @Column(name = "banner_from_date")
    private Instant bannerFromDate;

    @Column(name = "banner_until_date")
    private Instant bannerUntilDate;

    @Transient
    private String bannerFromStringDate;

    @Transient
    private String bannerUntilStringDate;

}
