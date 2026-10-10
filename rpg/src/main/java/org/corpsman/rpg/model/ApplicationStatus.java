package org.corpsman.rpg.model;

/**
 * Статус заявки на участие в игре.
 */
public enum ApplicationStatus {

    /**
     * Ожидает решения ведущего. До принятия пользователь не считается игроком.
     */
    PENDING,

    /**
     * Принята — пользователь становится игроком.
     */
    ACCEPTED,

    /**
     * Отклонена.
     */
    REJECTED,

    /**
     * Заявка отозвана самим игроком до рассмотрения.
     */
    WITHDRAWN;

    /**
     * Заявку можно подать заново: она отозвана или отклонена.
     */
    public boolean canResubmit() {
        return this == WITHDRAWN || this == REJECTED;
    }

    public String label() {
        return switch (this) {
            case PENDING -> "На рассмотрении";
            case ACCEPTED -> "Принята";
            case REJECTED -> "Отклонена";
            case WITHDRAWN -> "Отозвана";
        };
    }
}