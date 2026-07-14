package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.dto.MessageDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "jakarta")
public interface MessageMapper {

    // ==================================
    // Entity → DTO
    // ==================================
    @Mapping(target = "sender_id", source = "senderOidcId")
    @Mapping(target = "sender_name", ignore = true)
    @Mapping(target = "appointment_id", source = "appointment.id")
    @Mapping(target = "timestamp",
            expression = "java(message.getTimeStamp() != null ? message.getTimeStamp().toString() : null)")
    MessageDto toDto(Message message);

    // ==================================
    // DTO → Entity
    // ==================================
    @Mapping(target = "senderOidcId", source = "sender_id")
    @Mapping(target = "appointment",
            expression = "java(dto.appointment_id() != null ? de.chronos_live.chronos_date_api.domain.Appointment.findById(dto.appointment_id()) : null)")
    @Mapping(target = "timeStamp",
            expression = "java(dto.timestamp() != null ? java.time.Instant.parse(dto.timestamp()) : null)")
    Message toEntity(MessageDto dto);

    // ==================================
    // Collections
    // ==================================
    List<MessageDto> toDtoList(List<Message> messages);
    List<Message> toEntityList(List<MessageDto> dtos);
}
