package com.mymail.user.entities;


import com.mymail.common.SoftDeletable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends SoftDeletable {

    @Column(name = "idp_id", nullable = false, unique = true)
    private String idpId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;
}
