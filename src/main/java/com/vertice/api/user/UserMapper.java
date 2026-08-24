package com.vertice.api.user;

import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
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
@Mapper(componentModel = "spring", uses = ProtoStrings.class)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    User toEntity(UserCreateRequest request);

    // cref is the only nullable string column on User (see docs/specs/trainer-cref/spec.md) — a
    // real DB row for a CLIENT/ADMIN, or a TRAINER who hasn't set one, has cref == null, and
    // protobuf string setters reject null (ProtoStrings' own doc comment), so this needs the same
    // nullToEmpty treatment TrainingPlanMapper already uses for its nullable description field.
    @Mapping(target = "cref", qualifiedByName = "nullToEmpty")
    UserResponse toResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    void updateEntityFromRequest(UserRequest request, @MappingTarget User user);

    @ValueMapping(source = "ROLE_UNSPECIFIED", target = MappingConstants.NULL)
    @ValueMapping(source = "UNRECOGNIZED", target = MappingConstants.NULL)
    Role mapRole(com.vertice.api.generated.grpc.user.v1.Role role);
}
