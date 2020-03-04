package edu.uc.ltigradebook.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lti_gb_tokens")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class OauthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="token_generator")
    @SequenceGenerator(name="token_generator", sequenceName="lti_gb_token_seq", allocationSize=1)
    @Column(name = "token_id", nullable = false)
    private long tokenId;

    @Column(name = "token")
    private String token;

    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "status")
    private boolean status;

}
