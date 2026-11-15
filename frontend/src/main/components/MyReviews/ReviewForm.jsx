import React, { useEffect } from "react";
import { useForm } from "react-hook-form";
import { Form, Button } from "react-bootstrap";

function dateTimeLocalValue(dateTime) {
  return dateTime
    ? dateTime.slice(0, 16)
    : new Date().toISOString().slice(0, 16);
}

export default function ReviewForm({
  initialItemName = "",
  initialComments = "",
  initialStars = 5,
  initialDateServed,
  submitAction,
  submitButtonText = "Submit Review",
}) {
  // Stryker disable all
  const {
    register,
    formState: { errors },
    handleSubmit,
    setValue,
  } = useForm({
    defaultValues: {
      itemName: initialItemName,
      reviewerComments: initialComments,
      itemsStars: initialStars,
      dateItemServed: dateTimeLocalValue(initialDateServed),
    },
  });
  // Stryker restore all

  useEffect(() => {
    setValue("reviewerComments", initialComments);
    setValue("itemsStars", initialStars);
    if (initialDateServed) {
      setValue("dateItemServed", dateTimeLocalValue(initialDateServed));
    }
  }, [setValue, initialComments, initialStars, initialDateServed]);

  return (
    <Form onSubmit={handleSubmit(submitAction)}>
      <Form.Group className="mb-3">
        <Form.Label htmlFor="review-item-name">Item Name</Form.Label>
        <Form.Control
          id="review-item-name"
          type="text"
          {...register("itemName")}
          value={initialItemName}
          disabled
        />
      </Form.Group>

      <Form.Group className="mb-3">
        <Form.Label htmlFor="review-comments">Comments</Form.Label>
        <Form.Control
          id="review-comments"
          type="text"
          as="textarea"
          rows={3}
          isInvalid={Boolean(errors.reviewerComments)}
          {...register("reviewerComments")}
        />
      </Form.Group>

      <Form.Group className="mb-3">
        <Form.Label htmlFor="review-stars">Stars (1 to 5)</Form.Label>
        <Form.Select
          id="review-stars"
          type="number"
          isInvalid={Boolean(errors.itemsStars)}
          {...register("itemsStars", {
            valueAsNumber: true,
          })}
        >
          {[1, 2, 3, 4, 5].map((num) => (
            <option key={num} value={num}>
              {num}
            </option>
          ))}
        </Form.Select>
      </Form.Group>

      <Form.Group className="mb-3">
        <Form.Label htmlFor="review-date">
          Date and Time Item was Served
        </Form.Label>
        <Form.Control
          id="review-date"
          type="datetime-local"
          isInvalid={Boolean(errors.dateItemServed)}
          {...register("dateItemServed", {
            required: true,
          })}
        />
        <Form.Control.Feedback type="invalid">
          {errors.dateItemServed && "Date and time is required"}
        </Form.Control.Feedback>
      </Form.Group>

      <Button type="submit">{submitButtonText}</Button>
    </Form>
  );
}
