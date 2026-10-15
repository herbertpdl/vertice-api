package com.vertice.api.user;

import com.vertice.api.generated.grpc.user.v1.ConsentState;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.grpc.ProtoInstants;
import com.vertice.api.grpc.ProtoStrings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ValueMapping;

/**
 * proto3's {@code Role} enum always carries a zero value ({@code ROLE_UNSPECIFIED}) and an
 * {@code UNRECOGNIZED} catch-all for unknown wire values, neither of which has a matching
 * {@link com.vertice.api.user.Role} constant. Both are mapped to {@code null} here — the caller
 * (UserController) rejects {@code ROLE_UNSPECIFIED} before a request ever reaches the mapper, so
 * in practice this is unreachable, but MapStruct requires every source constant to be accounted
 * for.
 */
@Mapper(componentModel = "spring", uses = {ProtoStrings.class, ProtoInstants.class})
public interface UserMapper {

    // Consent and timestamps are server-set (UserService, User's lifecycle callbacks), never copied
    // from a request. An empty CPF is stored as NULL so V27's partial unique index allows many.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "cpf", qualifiedByName = "emptyToNull")
    @Mapping(target = "acceptedTermsVersion", ignore = true)
    @Mapping(target = "termsAcceptedAt", ignore = true)
    @Mapping(target = "healthDataConsentVersion", ignore = true)
    @Mapping(target = "healthDataConsentAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    User toEntity(UserCreateRequest request);

    // cref (a CLIENT/ADMIN, or a TRAINER who hasn't set one, see docs/specs/trainer-cref/spec.md),
    // cpf (an optional client CPF) and email (a deleted account) can be null on a real row, and
    // protobuf string setters reject null (ProtoStrings' own doc comment), so they get the same
    // nullToEmpty treatment TrainingPlanMapper already uses for its nullable description field.
    @Mapping(target = "email", qualifiedByName = "nullToEmpty")
    @Mapping(target = "cref", qualifiedByName = "nullToEmpty")
    @Mapping(target = "cpf", qualifiedByName = "nullToEmpty")
    @Mapping(target = "consent", source = "user")
    UserResponse toResponse(User user);

    @Mapping(target = "acceptedTermsVersion", qualifiedByName = "nullToEmpty")
    @Mapping(target = "termsAcceptedAt", qualifiedByName = "instantToString")
    @Mapping(target = "healthDataConsentVersion", qualifiedByName = "nullToEmpty")
    @Mapping(target = "healthDataConsentAt", qualifiedByName = "instantToString")
    ConsentState toConsentState(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "cpf", qualifiedByName = "emptyToNull")
    @Mapping(target = "acceptedTermsVersion", ignore = true)
    @Mapping(target = "termsAcceptedAt", ignore = true)
    @Mapping(target = "healthDataConsentVersion", ignore = true)
    @Mapping(target = "healthDataConsentAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntityFromRequest(UserRequest request, @MappingTarget User user);

    @ValueMapping(source = "ROLE_UNSPECIFIED", target = MappingConstants.NULL)
    @ValueMapping(source = "UNRECOGNIZED", target = MappingConstants.NULL)
    Role mapRole(com.vertice.api.generated.grpc.user.v1.Role role);
}
