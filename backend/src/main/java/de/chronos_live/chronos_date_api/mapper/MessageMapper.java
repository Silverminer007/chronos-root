package de.chronos_live.chronos_date_api.mapper;

import de.chronos_live.chronos_date_api.domain.Message;
import de.chronos_live.chronos_date_api.presentation.MessageDto;
import org.mapstruct.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Mapper(componentModel = "cdi")
public interface MessageMapper {

    // ==================================
    // Entity → DTO
    // ==================================
    @Mapping(target = "sender_id", source = "sender.id")
    @Mapping(target = "sender_name", expression = "java(message.getSender().getName())")
    @Mapping(target = "event_id", source = "event.id")
    @Mapping(target = "timestamp",
            expression = "java(message.getTimeStamp() != null ? message.getTimeStamp().toString() : null)")
    MessageDto toDto(Message message);

    // ==================================
    // DTO → Entity
    // ==================================
    @Mapping(target = "sender",
            expression = "java(dto.sender_id() != null ? de.chronos_live.chronos_date_api.domain.User.findById(dto.sender_id()) : null)")
    @Mapping(target = "event",
            expression = "java(dto.event_id() != null ? de.chronos_live.chronos_date_api.domain.Event.findById(dto.event_id()) : null)")
    @Mapping(target = "timeStamp",
            expression = "java(dto.timestamp() != null ? java.time.Instant.parse(dto.timestamp()) : null)")
    Message toEntity(MessageDto dto);

    // ==================================
    // Collections
    // ==================================
    List<MessageDto> toDtoList(List<Message> messages);
    List<Message> toEntityList(List<MessageDto> dtos);
}